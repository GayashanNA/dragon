package dragon.network.operations;

import java.util.ArrayList;

import dragon.network.NodeDescriptor;
import dragon.network.NodeStatus;
import dragon.network.messages.node.GetStatusErrorNMsg;
import dragon.network.messages.node.GetStatusNMsg;
import dragon.network.messages.node.NodeMessage;
import dragon.network.messages.node.StatusNMsg;

/**
 * Get the status of the daemons
 * @author aaron
 *
 */
public class GetStatusGroupOp extends GroupOp {
	private static final long serialVersionUID = 1L;
	
	public transient NodeStatus nodeStatus;
	
	public transient ArrayList<NodeStatus> dragonStatus;
	
	/**
	 * 
	 * @param success
	 * @param failure
	 */
	public GetStatusGroupOp(IOpSuccess success, IOpFailure failure) {
		super(success, failure);
		dragonStatus=new ArrayList<NodeStatus>();
	}
	
	public synchronized void aggregate(NodeStatus nodeStatus) {
		dragonStatus.add(nodeStatus);
	}

	/**
	 *
	 */
	@Override
	protected NodeMessage initiateNodeMessage(NodeDescriptor desc) {
		return new GetStatusNMsg();
	}

	/**
	 *
	 */
	@Override
	protected NodeMessage successNodeMessage() {
		return new StatusNMsg(nodeStatus);
	}

	/**
	 *
	 */
	@Override
	protected NodeMessage errorNodeMessage(String error) {
		return new GetStatusErrorNMsg(error);
	}

}
