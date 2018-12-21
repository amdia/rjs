import jason.RevisionFailedException;
import jason.architecture.AgArch;
import jason.asSemantics.ActionExec;
import jason.asSemantics.Message;
import jason.asSyntax.Atom;
import jason.asSyntax.ListTerm;
import jason.asSyntax.ListTermImpl;
import jason.asSyntax.Literal;
import jason.asSyntax.Term;
import ontologenius_msgs.OntologeniusServiceResponse;
import pointing_planner_msgs.PointingActionResult;
import semantic_route_description_msgs.Route;
import semantic_route_description_msgs.SemanticRouteResponse;
import supervisor.Code;
import supervisor.RosNode;
import supervisor.RouteImpl;
import supervisor.SemanticRouteResponseImpl;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.ros.node.DefaultNodeMainExecutor;
import org.ros.node.NodeConfiguration;
import org.ros.node.NodeMainExecutor;

import actionlib_msgs.GoalStatus;
import geometry_msgs.Pose;



public class RobotAgArch extends AgArch {
    
	@SuppressWarnings("unused")
	private Logger logger = Logger.getLogger(RobotAgArch.class.getName());
	RosNode                      	   m_rosnode;
	private NodeMainExecutor nodeMainExecutor = DefaultNodeMainExecutor.newDefault();
	private NodeConfiguration nodeConfiguration;
	URI masteruri;
	
    
    @Override
    public void init() throws Exception {
    
        super.init();
        
        masteruri = URI.create("http://140.93.7.251:11311");
		nodeConfiguration = NodeConfiguration.newPublic("140.93.7.251", masteruri);
		m_rosnode = new RosNode("node_test");
		nodeMainExecutor.execute(m_rosnode, nodeConfiguration);
        
    }      
   
    
    @Override
    public void act(ActionExec action) {
    	String action_name = action.getActionTerm().getFunctor();
    	Message msg = new Message("tell", getAgName(), "supervisor", "action_started("+action_name+")");
  
		try {
			sendMsg(msg);
		} catch (Exception e1) {
			e1.printStackTrace();
		}
		
    	
    	if(action_name.equals("compute_route")) {
    		// to remove the extra ""
    		String from = action.getActionTerm().getTerm(0).toString();
    		from = from.replaceAll("^\"|\"$", "");
    		
    		
    		ListTerm to_list;
    		Term to = (Term) action.getActionTerm().getTerm(1);
    		// if there is multiple places (toilet and atm cases)
    		if(to.isList()) {
    			to_list = (ListTerm) to;
    		}
    		// if there is only one place, we convert it to a list with one element for convenience
    		else {
    			to_list = new ListTermImpl();
    			to_list.add(to);
    		}
    		List<SemanticRouteResponse> routes = new ArrayList<SemanticRouteResponse>();
    		boolean at_least_one_ok = false;
    		// we get all the possible routes for the different places 
    		// (we will be able then to choose between the best toilet or atm to go)
    		for (Term t: to_list) {
    			// call the service to compute route
    			m_rosnode.call_get_route_c(from, 
    									t.toString(),
    									action.getActionTerm().getTerm(2).toString(), 
    									Boolean.parseBoolean(action.getActionTerm().getTerm(3).toString()));

    			SemanticRouteResponseImpl resp = new SemanticRouteResponseImpl();
    			// we wait the result return from the service
                do {
                	 resp = m_rosnode.get_get_route_resp();
                	if (resp != null) {
                		routes.add(resp);
                	}
                	try {
    					Thread.sleep(100);
    				} catch (InterruptedException e) {
    					e.printStackTrace();
    				}
                }while(resp == null);
                if(resp.getCode() == Code.ERROR.getCode()) {
                	action.setResult(false);
                	action.setFailureReason(new Atom("route_not_found"), "No route has been found");
                	
                }else {
                	at_least_one_ok = true;
                }
    		}        
    		if(!at_least_one_ok) {
    			actionExecuted(action);
    		}else {
	            RouteImpl route = select_best_route(routes);
	            
	            try {
	            	getTS().getAg().addBel(Literal.parseLiteral("route("+route.getRoute()+")"));
	            	getTS().getAg().addBel(Literal.parseLiteral("target_place("+route.getGoal()+")"));
	            	action.setResult(true);
	            	actionExecuted(action);
	            	
				} catch (RevisionFailedException e) {
					e.printStackTrace();
				}
    		}

    	} else if(action_name.equals("get_individual_type")) {
    		m_rosnode.call_onto_indivual_c("getType", action.getActionTerm().getTerm(0).toString());
    		OntologeniusServiceResponse places;
    		do {
    			places = m_rosnode.get_onto_individual_resp();
            	try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
            }while(places == null);
    		try {
				getTS().getAg().addBel(Literal.parseLiteral("possible_places("+places.getValues()+")"));
				action.setResult(true);
	        	actionExecuted(action);
			} catch (RevisionFailedException e) {
				e.printStackTrace();
			}
    	} else if(action_name.equals("get_onto_name")) {
    		// to remove the extra ""
    		String param = action.getActionTerm().getTerm(0).toString();
    		param = param.replaceAll("^\"|\"$", "");
    		
			m_rosnode.call_onto_indivual_c("find", param);
			OntologeniusServiceResponse place;
			do {
				place = m_rosnode.get_onto_individual_resp();
	        	try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
	        }while(place == null);
			if(place.getCode() == Code.OK.getCode()) {
				try {
					getTS().getAg().addBel(Literal.parseLiteral("onto_place("+place.getValues().get(0)+")"));
					action.setResult(true);
		        	actionExecuted(action);
				} catch (RevisionFailedException e) {
					e.printStackTrace();
				}
			}else {
				action.setResult(false);
				action.setFailureReason(new Atom("name_not_found"), "No place matching "+param+" has been found in the ontology");
	        	actionExecuted(action);
			}
	} else if(action_name.equals("get_human_abilities")) {
		try {
			getTS().getAg().addBel(Literal.parseLiteral("persona_asked(old)"));
		} catch (RevisionFailedException e) {
			e.printStackTrace();
		}
		action.setResult(true);
    	actionExecuted(action);
	} else if(action_name.equals("get_placements")) {
		// to remove the extra ""
		ArrayList<String> params = new ArrayList<String>();
		for(Term term : action.getActionTerm().getTerms()) {
			params.add(term.toString().replaceAll("^\"|\"$", ""));
		}
		
		String target = params.get(0);
		String direction = params.get(1);
		String human = params.get(2);
		m_rosnode.call_svp_planner(target, direction, human);
		PointingActionResult placements_result;
		do {
			placements_result = m_rosnode.get_get_placements_result();
		}while(placements_result == null);
		if(placements_result.getStatus().getStatus() == GoalStatus.SUCCEEDED) {
			try {
				Pose robot_pose = placements_result.getResult().getRobotPose().getPose();
				Pose human_pose = placements_result.getResult().getHumanPose().getPose();
				getTS().getAg().addBel(Literal.parseLiteral("robot_pos("+robot_pose.getPosition().getX()+","
																		+robot_pose.getPosition().getY()+","
																		+robot_pose.getPosition().getZ()+")"));
				getTS().getAg().addBel(Literal.parseLiteral("human_pos("+human_pose.getPosition().getX()+","
																		+human_pose.getPosition().getY()+","
																		+human_pose.getPosition().getZ()+")"));
				int nb_ld_to_point = placements_result.getResult().getPointedLandmarks().size();
				if(nb_ld_to_point == 0) {
					getTS().getAg().addBel(Literal.parseLiteral("ld_to_point(None)"));
				} else if(nb_ld_to_point==1) {
					getTS().getAg().addBel(Literal.parseLiteral("ld_to_point("+placements_result.getResult().getPointedLandmarks().get(0)+")"));
				} else if(nb_ld_to_point==2){
					getTS().getAg().addBel(Literal.parseLiteral("ld_to_point("+placements_result.getResult().getPointedLandmarks().get(0)+","
																		  	  +placements_result.getResult().getPointedLandmarks().get(1)+")"));
				}
			} catch (RevisionFailedException e) {
				e.printStackTrace();
			}
			action.setResult(true);
        	actionExecuted(action);
		}else {
			action.setResult(false);
			action.setFailureReason(new Atom("svp_failure"), "SVP planner goal status :"+placements_result.getStatus().getStatus());
			actionExecuted(action);
		}
	}
    	else {
			super.act(action);
		}
    }
    
    
    public RouteImpl select_best_route(List<SemanticRouteResponse> routes_resp_list) {
    	RouteImpl best_route = new RouteImpl();
    	float min_cost = Float.MAX_VALUE;
    	if (routes_resp_list.size() > 0) {
    		for (SemanticRouteResponse route_resp : routes_resp_list) {
    			List<Route> routes = route_resp.getRoutes();
    			float[] costs = route_resp.getCosts();
    			List<String> goals = route_resp.getGoals();
    			
    			for (int i = 0; i < routes.size(); i++) {
    				if (costs[i] < min_cost) {
    					best_route.setRoute(routes.get(i).getRoute());
    					best_route.setGoal(goals.get(i));
    					min_cost = costs[i];
    				}
    			}
    		}
    	}
    	
    	return best_route;
    	
    }


	@Override
	public void actionExecuted(ActionExec act) {
		String action_name = act.getActionTerm().getFunctor();
		Message msg;
		if(act.getResult()) {
    		msg = new Message("tell", getAgName(), "supervisor", "action_over("+action_name+")");
    	}else {
//    		msg = new Message("tell", getAgName(), "supervisor", "action_failed("+action_name+","+new StringTermImpl(act.getFailureReason().toString())+")");
    		msg = new Message("tell", getAgName(), "supervisor", "action_failed("+action_name+","+act.getFailureReason().toString()+")");
    	}
    	try {
			sendMsg(msg);
		} catch (Exception e1) {
			e1.printStackTrace();
		}
		super.actionExecuted(act);
	}
    
    
    
};


