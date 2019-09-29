package dragon.metrics;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import dragon.Config;
import dragon.LocalCluster;
import dragon.network.Node;

public class Metrics extends Thread {
	private static Log log = LogFactory.getLog(Metrics.class);
	private Node node;
	
	private TopologyMetricMap samples;
	
	public Metrics(Node node){
		log.debug("metrics initialized");
		samples=new TopologyMetricMap((int)node.getConf().getDragonMetricsSampleHistory());
		this.node = node;
	}
	
	public ComponentMetricMap getMetrics(String topologyId){
		log.debug("gettings samples for ["+topologyId+"]");
		synchronized(samples){
			return samples.get(topologyId);
		}
	}
	
	@Override
	public void run(){
		while(!isInterrupted()){
			try {
				sleep((int)node.getConf().getDragonMetricsSamplePeriodMs());
			} catch (InterruptedException e) {
				log.info("shutting down");
			}
			synchronized(samples){
				for(String topologyId : node.getLocalClusters().keySet()){
					log.info("sampling topology ["+topologyId+"]");
					LocalCluster localCluster = node.getLocalClusters().get(topologyId);
					for(String componentId : localCluster.getSpouts().keySet()){
						for(Integer taskId : localCluster.getSpouts().get(componentId).keySet()){
							Sample sample = new Sample(localCluster.getSpouts().get(componentId).get(taskId));
							
							samples.put(topologyId, componentId, taskId, sample);
						}
					}
					for(String componentId : localCluster.getBolts().keySet()){
						for(Integer taskId : localCluster.getBolts().get(componentId).keySet()){
							Sample sample = new Sample(localCluster.getBolts().get(componentId).get(taskId));
							
							samples.put(topologyId, componentId, taskId, sample);
						}
					}
				}
			}
		}
	}

}
