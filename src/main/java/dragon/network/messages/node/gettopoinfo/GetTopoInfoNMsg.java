package dragon.network.messages.node.gettopoinfo;

import dragon.network.messages.node.NodeMessage;

/**
 * @author aaron
 *
 */
public class GetTopoInfoNMsg extends NodeMessage {
	private static final long serialVersionUID = 1383319162954166063L;
	
	/**
	 * 
	 */
	public GetTopoInfoNMsg() {
		super(NodeMessage.NodeMessageType.GET_TOPOLOGY_INFORMATION);
	}
}