package dragon.network;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import dragon.LocalCluster;
import dragon.network.messages.service.RunTopologyMessage;
import dragon.network.messages.service.ServiceMessage;
import dragon.network.messages.service.TopologyExistsMessage;
import dragon.network.messages.service.RunFailedMessage;
import dragon.network.messages.node.NodeMessage;
import dragon.network.messages.node.PrepareTopologyMessage;
import dragon.network.messages.service.NodeContextMessage;

public class ServiceProcessor extends Thread {
	private static Log log = LogFactory.getLog(ServiceProcessor.class);
	private boolean shouldTerminate=false;
	private Node node;
	public ServiceProcessor(Node node) {
		this.node=node;
		log.debug("starting service processor");
		start();
	}
	
	@Override
	public void run(){
		while(!shouldTerminate){
			ServiceMessage command = node.getComms().receiveServiceMessage();
			switch(command.getType()){
			case RUN_TOPOLOGY:
				RunTopologyMessage scommand = (RunTopologyMessage) command;
				if(node.getLocalClusters().containsKey(scommand.topologyName)){
					node.getComms().sendServiceMessage(new TopologyExistsMessage(scommand.topologyName));
				} else {
					if(!node.storeJarFile(scommand.topologyName,scommand.topologyJar)) {
						node.getComms().sendServiceMessage(new RunFailedMessage(scommand.topologyName,"could not store the topology jar"));
						continue;
					}
					if(!node.loadJarFile(scommand.topologyName)) {
						node.getComms().sendServiceMessage(new RunFailedMessage(scommand.topologyName,"could not load the topology jar"));				
						continue;
					}
					LocalCluster cluster=new LocalCluster(node);
					cluster.submitTopology(scommand.topologyName, scommand.conf, scommand.dragonTopology, false);
					node.getLocalClusters().put(scommand.topologyName, cluster);
					node.createStartupTopology(scommand.topologyName);
					for(NodeDescriptor desc : scommand.dragonTopology.getReverseEmbedding().keySet()) {
						if(!desc.equals(node.getComms().getMyNodeDescriptor())) {
							NodeMessage message = new PrepareTopologyMessage(scommand.topologyName,scommand.conf,scommand.dragonTopology,scommand.topologyJar);
							node.getComms().sendNodeMessage(desc, message);
						}
					}
				}
				break;
			case GET_NODE_CONTEXT:
				node.getComms().sendServiceMessage(new NodeContextMessage(node.getNodeProcessor().getContext()));
				break;
			default:
			}
		}
	}
}
