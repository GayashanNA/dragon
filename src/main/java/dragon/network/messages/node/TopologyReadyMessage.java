package dragon.network.messages.node;

public class TopologyReadyMessage extends NodeMessage {

	/**
	 * 
	 */
	private static final long serialVersionUID = -1794256917559159190L;
	public String topologyId;
	public TopologyReadyMessage(String topologyId) {
		super(NodeMessage.NodeMessageType.TOPOLOGY_READY);
		this.topologyId=topologyId;
	}

}
