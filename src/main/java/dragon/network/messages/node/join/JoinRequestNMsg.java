package dragon.network.messages.node.join;

import dragon.network.messages.node.NodeMessage;

/**
 * @author aaron
 *
 */
public class JoinRequestNMsg extends NodeMessage {
	private static final long serialVersionUID = 4994962717012324017L;

	/**
	 * 
	 */
	public JoinRequestNMsg() {
		super(NodeMessage.NodeMessageType.JOIN_REQUEST);
	}

}