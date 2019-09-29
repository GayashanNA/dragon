package dragon.network.operations;

import java.io.Serializable;
import java.util.HashSet;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import dragon.network.NodeDescriptor;
import dragon.network.comms.DragonCommsException;
import dragon.network.comms.IComms;
import dragon.network.messages.Message;
import dragon.network.messages.node.NodeMessage;
import dragon.network.messages.service.ServiceMessage;

public class GroupOperation implements Serializable {
	private static final long serialVersionUID = 7500196228211761411L;
	private static Log log = LogFactory.getLog(GroupOperation.class);
	protected transient HashSet<NodeDescriptor> group;
	private long id;
	private NodeDescriptor sourceDesc;
	private Message orig;
	
	public GroupOperation(Message orig) {
		this.orig = orig;
		group = new HashSet<NodeDescriptor>();
	}
	
	public void add(NodeDescriptor desc) {
		group.add(desc);
	}
	
	protected boolean remove(NodeDescriptor desc) {
		group.remove(desc);
		return group.isEmpty();
	}

	public void init(NodeDescriptor desc,long groupOperationCounter) {
		this.sourceDesc=desc;
		this.id=groupOperationCounter;
	}
	
	public Long getId() {
		return this.id;
	}
	
	public NodeDescriptor getSourceDesc() {
		return this.sourceDesc;
	}
	
	protected void sendGroupNodeMessage(IComms comms,NodeDescriptor desc, NodeMessage message, Message to) throws DragonCommsException {
		message.setGroupOperation(this);
		comms.sendNodeMessage(desc,message,to);
	}

	public void initiate(IComms comms) {
		for(NodeDescriptor desc : group) {
			if(!desc.equals(sourceDesc)) {
				try {
					sendGroupNodeMessage(comms,desc, initiateNodeMessage() ,orig);
				} catch (DragonCommsException e) {
					fail(comms,"network errors prevented group operation");
				}
			}
		}
	}
	
	public void sendSuccess(IComms comms) {
		if(!getSourceDesc().equals(comms.getMyNodeDescriptor())) {
			NodeMessage tsm = successNodeMessage();
			try {
				sendGroupNodeMessage(comms,getSourceDesc(), tsm, orig);
			} catch (DragonCommsException e) {
				log.fatal("network errors prevented group operation");
			}
		} else {
			receiveSuccess(comms,comms.getMyNodeDescriptor());
		}
	}
	
	public void sendError(IComms comms,String error) {
		try {
			comms.sendNodeMessage(getSourceDesc(),errorNodeMessage(error),orig);
		} catch (DragonCommsException e) {
			log.fatal("network errors prevented group operation");
		}
	}
	
	public void receiveSuccess(IComms comms, NodeDescriptor desc) {
		if(remove(desc)) {
			success(comms);
		}
	}
	
	public void receiveError(IComms comms, NodeDescriptor desc,String error) {
		remove(desc);
		fail(comms,error);
	}
	
	public void success(IComms comms) {
		try {
			comms.sendServiceMessage(successServiceMessage(),orig);
		} catch (DragonCommsException e1) {
			// TODO: cleanup
		}
	}
	
	public void fail(IComms comms, String error) {
		log.fatal(error);
		try {
			comms.sendServiceMessage(failServiceMessage(error),orig);
		} catch (DragonCommsException e1) {
			// TODO: cleanup
		}
	}
	
	/*
	 * Appropriate message are provided by the subclass.
	 */

	protected NodeMessage initiateNodeMessage() {
		return null;
	}
	protected NodeMessage successNodeMessage() {
		return null;
	}
	protected NodeMessage errorNodeMessage(String error) {
		return null;
	}
	protected ServiceMessage successServiceMessage() {
		return null;
	}
	protected ServiceMessage failServiceMessage(String error) {
		return null;
	}

}