package dragon.tuple;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author aaron
 *
 */
public class Tuple implements IRecyclable, Serializable {
	@SuppressWarnings("unused")
	private final static Logger log = LogManager.getLogger(Tuple.class);
	private static final long serialVersionUID = -8616313770722910200L;
	
	/**
	 * 
	 */
	private String sourceComponent;
	
	/**
	 * 
	 */
	private String sourceStreamId;
	
	/**
	 * 
	 */
	private Integer sourceTaskId;
	
	/**
	 * 
	 */
	private Fields fields;
	
	/**
	 * @author aaron
	 *
	 */
	public static enum Type {
		APPLICATION,
		TERMINATE,
		FREEZE,
		CHECKPOINT,
		PRECYCLE
	}
	
	/**
	 * 
	 */
	private Type type;
	
	/**
	 * 
	 */
	public Tuple() {
		type=Type.APPLICATION;
		fields=new Fields();
	}
	
	/**
	 * @param fields
	 */
	public Tuple(Fields fields) {
		type=Type.APPLICATION;
		this.fields=fields.copy();
	}
	
	/**
	 * @param fields
	 * @param values
	 */
	public Tuple(Fields fields,Values values) {
		type=Type.APPLICATION;
		this.fields=fields.copy();
		setValues(values);
	}
	
	/**
	 * @param values
	 */
	public void setValues(Values values) {
		for(int i=0;i<values.size();i++) {
			fields.set(i, values.get(i));
		}
	}
	
	/**
	 * @param type
	 */
	public void setType(Type type) {
		this.type=type;
	}
	
	/**
	 * @return
	 */
	public Type getType() {
		return type;
	}
	
	/**
	 * 
	 */
	public void clearValues() {
		for(int i=0;i<fields.size();i++) {
			fields.set(i, null);
		}
	}
	
	/**
	 * @param index
	 * @return
	 */
	public Object getValue(int index){
		return fields.getValues()[index];
	}
	
	/**
	 * @return
	 */
	public Object[] getValues(){
		return fields.getValues();
	}
	
	/**
	 * @return
	 */
	public Fields getFields() {
		return fields;
	}
	
	/**
	 * @param fields
	 */
	public void setFields(Fields fields) {
		this.fields=fields;
	}
	
	/**
	 * @param fieldName
	 * @return
	 */
	public Object getValueByField(String fieldName) {
		return fields.get(fields.getFieldMap().get(fieldName));
	}
	
	/**
	 * @param componentId
	 */
	public void setSourceComponent(String componentId) {
		this.sourceComponent=componentId;
	}
	
	/**
	 * @param streamId
	 */
	public void setSourceStreamId(String streamId) {
		this.sourceStreamId=streamId;
	}
	
	/**
	 * @param taskId
	 */
	public void setSourceTaskId(Integer taskId) {
		this.sourceTaskId = taskId;
	}
	
	/**
	 * @return
	 */
	public String getSourceComponent() {
		return sourceComponent;
	}
	
	/**
	 * @return
	 */
	public String getSourceStreamId() {
		return sourceStreamId;
	}
	
	/**
	 * @return
	 */
	public Integer getSourceTaskId() {
		return sourceTaskId;
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "source("+sourceComponent+":"+sourceStreamId+":"+sourceTaskId+")<"+fields.getValues().toString()+">";
	}

	/* (non-Javadoc)
	 * @see dragon.tuple.IRecyclable#recycle()
	 */
	@Override
	public void recycle() {
		clearValues();
		sourceComponent=null;
		sourceStreamId=null;
		sourceTaskId=null;
		type=Tuple.Type.APPLICATION;
	}

	/* (non-Javadoc)
	 * @see dragon.tuple.IRecyclable#newRecyclable()
	 */
	@Override
	public IRecyclable newRecyclable() {
		return new Tuple(this.fields);
	}
	
	/**
	 * @param out
	 * @throws IOException
	 */
	public void sendToStream(ObjectOutputStream out) throws IOException {
		out.writeUTF(sourceComponent);
		out.writeUTF(sourceStreamId);
		out.writeInt(sourceTaskId);
		out.writeUTF(type.name());
		fields.sendToStream(out);
	}
	
	/**
	 * @param in
	 * @return
	 * @throws ClassNotFoundException
	 * @throws IOException
	 */
	public static Tuple readFromStream(ObjectInputStream in) throws ClassNotFoundException, IOException {
		String sourceComponent = in.readUTF();
		String sourceStreamId = in.readUTF();
		Integer sourceTaskId = in.readInt();
		Type type = Type.valueOf(in.readUTF());
		Fields fields = Fields.readFromStream(in);
		Tuple t = RecycleStation.getInstance().getTupleRecycler(fields.getFieldNamesAsString()).newObject();
		t.setSourceComponent(sourceComponent);
		t.setSourceStreamId(sourceStreamId);
		t.setSourceTaskId(sourceTaskId);
		t.setFields(fields);
		t.setType(type);
		return t;
	}

}
