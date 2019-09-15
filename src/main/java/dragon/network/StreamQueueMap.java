package dragon.network;

import java.util.HashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import dragon.NetworkTask;
import dragon.utils.NetworkTaskBuffer;

public class StreamQueueMap extends HashMap<String,NetworkTaskBuffer>{
	private static Log log = LogFactory.getLog(StreamQueueMap.class);

	/**
	 * 
	 */
	private static final long serialVersionUID = 5785890558744691873L;

	private int bufferSize;
	
	public StreamQueueMap(int bufferSize){
		this.bufferSize=bufferSize;
	}
	
	public void put(NetworkTask task) throws InterruptedException {
		String streamId = task.getTuple().getSourceStreamId();
		NetworkTaskBuffer buffer=get(streamId);
		buffer.put(task);
	}
	
	public NetworkTaskBuffer getBuffer(NetworkTask task){
		return get(task.getTuple().getSourceStreamId());
	}

	public void prepare(String streamId) {
		if(!containsKey(streamId)) {
			NetworkTaskBuffer buffer=new NetworkTaskBuffer(bufferSize);
			put(streamId,buffer);
		}
	}

	public void drop(String streamId) {
		if(get(streamId).getNumElements()!=0) {
			log.error("dropping stream ["+streamId+"] of size ["+get(streamId).getNumElements()+"]");
		}
		remove(streamId);
	}
}
