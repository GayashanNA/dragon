package dragon.topology;

import java.util.HashMap;

import dragon.Constants;
import dragon.tuple.Fields;

public class OutputFieldsDeclarer {
	public HashMap<String,Fields> streamFields;
	public HashMap<String,Fields> directStreamFields;
	
	public OutputFieldsDeclarer() {
		streamFields = new HashMap<String,Fields>();
		directStreamFields = new HashMap<String,Fields>();
	}
	
	public void declare(Fields fields) {
		declare(Constants.DEFAULT_STREAM,fields);
	}
	
	public void declare(boolean direct,Fields fields) {
		if(direct==false){
			declare(Constants.DEFAULT_STREAM,fields);
		} else {
			directStreamFields.put(Constants.DEFAULT_STREAM,fields);
		}
	}
	
	public void declare(String streamId,Fields fields) {
		streamFields.put(streamId, fields);
	}
	
	public void declareStream(String streamId,Fields fields) {
		declare(streamId,fields);
	}
	
	public void declareStream(String streamId,boolean direct,Fields fields) {
		if(!direct){
			declare(streamId,fields);
		} else {
			directStreamFields.put(streamId, fields);
		}
	}
	
	public Fields getFields(String streamId) {
		return streamFields.get(streamId);
	}
	
	public Fields getFieldsDirect(String streamId) {
		return directStreamFields.get(streamId);
	}
	
}
