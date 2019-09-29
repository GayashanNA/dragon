package dragon.tuple;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.HashSet;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class NetworkTask extends RecyclableObject implements Serializable {
	private static Log log = LogFactory.getLog(NetworkTask.class);
	private static final long serialVersionUID = 6164101511657361631L;
	private Tuple tuple;
	private HashSet<Integer> taskIds;
	private String componentId;
	private String topologyId;

	public NetworkTask() {
	
	}
	
	public NetworkTask(Tuple tuple,HashSet<Integer> taskIds,String componentId, String topologyId) {
		init(tuple,taskIds,componentId,topologyId);
	}
	
	public void init(Tuple tuple,HashSet<Integer> taskIds,String componentId, String topologyId) {
		this.tuple=tuple;
		tuple.shareRecyclable(1);
		this.taskIds=taskIds;
		this.componentId=componentId;
		this.topologyId=topologyId;
	}
	
	public Tuple getTuple() {
		return tuple;
	}
	
	public HashSet<Integer> getTaskIds(){
		return taskIds;
	}
	
	public String getComponentId() {
		return componentId;
	}
	
	public String getTopologyId() {
		return topologyId;
	}

	@Override
	public String toString() {
		return tuple.toString();
	}

	@Override
	public void recycle() {
		tuple.crushRecyclable(1); 
		tuple=null;
		taskIds=null;
		componentId=null;
		topologyId=null;
	}

	@Override
	public IRecyclable newRecyclable() {
		return new NetworkTask();
	}
	
	public void sendToStream(ObjectOutputStream out) throws IOException {
		tuple.sendToStream(out);
		out.writeObject(taskIds);
		out.writeObject(componentId);
		out.writeObject(topologyId);
	}
	
	@SuppressWarnings("unchecked")
	public static NetworkTask readFromStream(ObjectInputStream in) throws ClassNotFoundException, IOException {
		Tuple t = Tuple.readFromStream(in);
		HashSet<Integer> taskIds = (HashSet<Integer>) in.readObject();
		String componentId = (String) in.readObject();
		String topologyId = (String) in.readObject();
		NetworkTask nt = RecycleStation.getInstance().getNetworkTaskRecycler().newObject();
		nt.init(t, taskIds, componentId, topologyId);
		t.crushRecyclable(1);
		return nt;
	}
}