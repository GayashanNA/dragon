package dragon.network.operations;

import dragon.network.messages.Message;

import dragon.network.messages.node.NodeMessage;
import dragon.network.messages.node.ResumeTopologyErrorMessage;
import dragon.network.messages.node.ResumeTopologyMessage;
import dragon.network.messages.node.TopologyResumedMessage;
import dragon.network.messages.service.ServiceMessage;

public class ResumeTopologyGroupOperation extends GroupOperation {
	private static final long serialVersionUID = -8685556477082460093L;
	private final String topologyId;
	public ResumeTopologyGroupOperation(Message orig) {
		super(orig);
		topologyId = ((dragon.network.messages.service.ResumeTopologyMessage)orig).topologyId;
		
	}
	
	@Override
	protected NodeMessage initiateNodeMessage() {
		return new ResumeTopologyMessage(topologyId);
	}
	
	@Override
	protected NodeMessage successNodeMessage() {
		return new TopologyResumedMessage(topologyId);
	}
	
	@Override 
	protected NodeMessage errorNodeMessage(String error) {
		return new ResumeTopologyErrorMessage(topologyId,error);
	}
	
	@Override
	protected ServiceMessage successServiceMessage() {
		return new dragon.network.messages.service.TopologyResumedMessage(topologyId);
	}
	
	@Override
	protected ServiceMessage failServiceMessage(String error) {
		return new dragon.network.messages.service.ResumeTopologyErrorMessage(topologyId,error);
		
	}
}