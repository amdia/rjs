package supervisor;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.ros.exception.RemoteException;
import org.ros.exception.RosRuntimeException;
import org.ros.exception.ServiceNotFoundException;
import org.ros.message.Duration;
import org.ros.namespace.GraphName;
import org.ros.node.AbstractNodeMain;
import org.ros.node.ConnectedNode;
import org.ros.node.service.ServiceClient;
import org.ros.node.service.ServiceResponseListener;

import com.github.rosjava_actionlib.ActionClient;
import com.github.rosjava_actionlib.ActionClientListener;
import com.github.rosjava_actionlib.ClientState;

import actionlib_msgs.GoalStatus;
import actionlib_msgs.GoalStatusArray;
import ontologenius_msgs.OntologeniusService;
import ontologenius_msgs.OntologeniusServiceRequest;
import ontologenius_msgs.OntologeniusServiceResponse;
import pointing_planner_msgs.PointingActionFeedback;
import pointing_planner_msgs.PointingActionGoal;
import pointing_planner_msgs.PointingActionResult;
import pointing_planner_msgs.PointingGoal;
import semantic_route_description_msgs.SemanticRoute;
import semantic_route_description_msgs.SemanticRouteRequest;
import semantic_route_description_msgs.SemanticRouteResponse;

/***
 * ROS node to be used by Jason
 * 
 * @author Google Code
 * @version 1.0
 * @since 2014-06-02
 *
 */
public class RosNode extends AbstractNodeMain {
	@SuppressWarnings("unused")
	private String name;
	private Logger logger = Logger.getLogger(RosNode.class.getName());
	
	private ServiceClient<OntologeniusServiceRequest, OntologeniusServiceResponse> onto_individual_c;
	private ServiceClient<SemanticRouteRequest, SemanticRouteResponse> get_route_c;
	private ActionClient<PointingActionGoal, PointingActionFeedback, PointingActionResult> get_placements_ac;
	
	private OntologeniusServiceResponseImpl onto_individual_resp;
	private SemanticRouteResponseImpl get_route_resp;
	private PointingActionResult get_placements_result;
	
	
	public RosNode(String name) {
		this.name = name;
	}

	public GraphName getDefaultNodeName() {
	    return GraphName.of("clients_node");
	  }

	  @Override
	  public void onStart(final ConnectedNode connectedNode) {
		  try {
			  onto_individual_c = connectedNode.newServiceClient("/ontologenius/individual",OntologeniusService._TYPE);
			  
			  get_route_c = connectedNode.newServiceClient("/semantic_route_description/get_route", SemanticRoute._TYPE);
			  
			  get_placements_ac = new ActionClient<PointingActionGoal, PointingActionFeedback, PointingActionResult>
			  (connectedNode, "/pointing_planner/PointingPlanner", PointingActionGoal._TYPE, PointingActionFeedback._TYPE, PointingActionResult._TYPE);
			  // too many useless loginfo in the class ActionClient (modified ActionClient by amdia to add the setLogLevel method)
			  get_placements_ac.setLogLevel(Level.OFF);
			  
			  ActionClientListener<PointingActionFeedback, PointingActionResult> client_listener 
			  = new ActionClientListener<PointingActionFeedback, PointingActionResult>(){

				  public void feedbackReceived(PointingActionFeedback arg0) {}

				  public void resultReceived(PointingActionResult result) {
					  get_placements_result = result;
				  }

				  public void statusReceived(GoalStatusArray arg0) {}


			  };
			  get_placements_ac.attachListener(client_listener);
		  } catch (ServiceNotFoundException e) {
			  throw new RosRuntimeException(e);
		  }
	  }
	  
	  public void call_onto_indivual_c(String action, String param) {
		  onto_individual_resp = null;
		  final OntologeniusServiceRequest request = onto_individual_c.newMessage();
		  request.setAction(action);
          request.setParam(param);
          onto_individual_c.call(request, new ServiceResponseListener<OntologeniusServiceResponse>() {
		      public void onSuccess(OntologeniusServiceResponse response) {
		    	  onto_individual_resp = new OntologeniusServiceResponseImpl();
		    	  if(response.getValues().isEmpty()) {
		    		  onto_individual_resp.setCode((short) Code.ERROR.getCode());
		    	  }else {
		    		  onto_individual_resp.setCode((short) Code.OK.getCode());
		    		  onto_individual_resp.setValues(response.getValues());
		    	  }
		      }

		      public void onFailure(RemoteException e) {
		        throw new RosRuntimeException(e);
		      }
		    });
	  }
	  
	  public void call_get_route_c(String from, String to, String persona, boolean signpost) {
		  get_route_resp = null;
		  final SemanticRouteRequest request = get_route_c.newMessage();
		  request.setFrom(from);
          request.setTo(to);
          request.setPersona(persona);
          request.setSignpost(signpost);
          get_route_c.call(request, new ServiceResponseListener<SemanticRouteResponse>() {
		      public void onSuccess(SemanticRouteResponse response) {
		    	  get_route_resp = new SemanticRouteResponseImpl(response.getCosts(), response.getGoals(), response.getRoutes());
		    	  if(response.getRoutes().isEmpty()) {
		    		  get_route_resp.setCode(Code.ERROR.getCode());
		    	  }else {
		    		  get_route_resp.setCode(Code.OK.getCode());
		    	  }
		      }

		      public void onFailure(RemoteException e) {
		        throw new RosRuntimeException(e);
		      }
		    });
	  }
	  
	  public void call_svp_planner(String target_ld, String direction_ld, String human) {
		  get_placements_result = null;
		  boolean serverStarted = get_placements_ac.waitForActionServerToStart(new Duration(10));
		  // TODO send info to supervisor agent
	        if (serverStarted) {
	            logger.info("Action server started.\n");
	        } else {
	        	logger.info("No actionlib svp server found ");
	        }
		  PointingActionGoal goal_msg;
		  goal_msg = get_placements_ac.newGoalMessage();
		  PointingGoal svp_goal = goal_msg.getGoal();
		  svp_goal.setHuman(human);
		  svp_goal.setDirectionLandmark(direction_ld);
		  svp_goal.setTargetLandmark(target_ld);
		  goal_msg.setGoal(svp_goal);
		  get_placements_ac.sendGoal(goal_msg);
		  while(get_placements_ac.getGoalState() != ClientState.DONE) {
			  sleep(100);
		  }
	  }
	  
	  public OntologeniusServiceResponseImpl get_onto_individual_resp() {
		  return onto_individual_resp;
	  }
	  
	  public SemanticRouteResponseImpl get_get_route_resp() {
		  return get_route_resp;
	  }
	  
	  
	  public PointingActionResult get_get_placements_result() {
		return get_placements_result;
	}


	void sleep(long msec) {
	        try {
	            Thread.sleep(msec);
	        } catch (InterruptedException ex) {
	        }
	    }
	  

}