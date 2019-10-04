package dragon;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import dragon.grouping.AbstractGrouping;
import dragon.network.Node;
import dragon.network.operations.GroupOperation;
import dragon.network.operations.TerminateTopologyGroupOperation;
import dragon.spout.SpoutOutputCollector;
import dragon.task.InputCollector;
import dragon.task.OutputCollector;
import dragon.task.TopologyContext;
import dragon.topology.BoltDeclarer;
import dragon.topology.DragonTopology;
import dragon.topology.GroupingsSet;
import dragon.topology.OutputFieldsDeclarer;
import dragon.topology.SpoutDeclarer;
import dragon.topology.StreamMap;
import dragon.topology.base.Bolt;

import dragon.topology.base.Component;


import dragon.topology.base.Spout;
import dragon.tuple.Fields;
import dragon.tuple.NetworkTask;
import dragon.tuple.RecycleStation;
import dragon.tuple.Tuple;
import dragon.tuple.Values;
import dragon.utils.NetworkTaskBuffer;


public class LocalCluster {
	private final static Log log = LogFactory.getLog(LocalCluster.class);
	private HashMap<String,HashMap<Integer,Bolt>> bolts;
	private HashMap<String,HashMap<Integer,Spout>> spouts;
	private HashMap<String,Config> spoutConfs;
	private HashMap<String,Config> boltConfs;
	private ArrayList<Thread> componentExecutorThreads;
	private ArrayList<Thread> networkExecutorThreads;
	
	@SuppressWarnings("rawtypes")
	private HashMap<Class,HashSet<GroupOperation>> groupOperations;
	
	private String topologyName;
	private Config conf;
	private DragonTopology dragonTopology;
	
	private LinkedBlockingQueue<NetworkTaskBuffer> outputsPending;
	private LinkedBlockingQueue<Component> componentsPending;

	private Thread tickThread;
	private Thread tickCounterThread;
	private long tickTime=0;
	private long tickCounterTime=0;
	
	private HashMap<String,Integer> boltTickCount;
	
	private int totalParallelismHint=0;
	
	private volatile boolean shouldTerminate=false;
	
	private final Node node;
	
	private final Fields tickFields=new Fields("tick");
	

	private class BoltPrepare {
		public Bolt bolt;
		public TopologyContext context;
		public OutputCollector collector;
		public BoltPrepare(Bolt bolt,TopologyContext context,OutputCollector collector) {
			this.bolt=bolt;
			this.context=context;
			this.collector=collector;
		}
	}
	
	private ArrayList<BoltPrepare> boltPrepareList;
	
	private class SpoutOpen {
		public Spout spout;
		public TopologyContext context;
		public SpoutOutputCollector collector;
		public SpoutOpen(Spout spout,TopologyContext context,SpoutOutputCollector collector) {
			this.spout=spout;
			this.context=context;
			this.collector=collector;
		}
	}
	
	private ArrayList<SpoutOpen> spoutOpenList;
	
	@SuppressWarnings("rawtypes")
	public LocalCluster(){
		this.node=null;
		groupOperations = new HashMap<Class,HashSet<GroupOperation>>();
	}
	
	@SuppressWarnings("rawtypes")
	public LocalCluster(Node node) {
		this.node=node;
		groupOperations = new HashMap<Class,HashSet<GroupOperation>>();
	}
	
	public void submitTopology(String topologyName, Config conf, DragonTopology dragonTopology) {
		Config lconf=null;
		try {
			lconf = new Config(Constants.DRAGON_PROPERTIES);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		lconf.putAll(conf);
		submitTopology(topologyName, lconf, dragonTopology, true);
	}

	public void submitTopology(String topologyName, Config conf, DragonTopology dragonTopology, boolean start) {
		this.topologyName=topologyName;
		this.conf=conf;
		this.dragonTopology=dragonTopology;
		outputsPending = new LinkedBlockingQueue<NetworkTaskBuffer>();
		componentsPending = new LinkedBlockingQueue<Component>();
		//networkExecutorService = (ThreadPoolExecutor) Executors.newFixedThreadPool((Integer)conf.getDragonLocalclusterThreads());
		networkExecutorThreads = new ArrayList<Thread>();
		if(!start) {
			spoutOpenList = new ArrayList<SpoutOpen>();
			boltPrepareList = new ArrayList<BoltPrepare>();
		}
		
		RecycleStation.getInstance().createTupleRecycler(new Tuple(tickFields));
		
		// allocate spouts and open them
		spouts = new HashMap<String,HashMap<Integer,Spout>>();
		spoutConfs = new HashMap<String,Config>();
		for(String spoutId : dragonTopology.getSpoutMap().keySet()) {
			if(dragonTopology.getReverseEmbedding()!=null &&
					!dragonTopology.getReverseEmbedding().contains(node.getComms().getMyNodeDescriptor(),spoutId)) continue;
			log.debug("allocating spout ["+spoutId+"]");
			spouts.put(spoutId, new HashMap<Integer,Spout>());
			HashMap<Integer,Spout> hm = spouts.get(spoutId);
			SpoutDeclarer spoutDeclarer = dragonTopology.getSpoutMap().get(spoutId);
			ArrayList<Integer> taskIds=new ArrayList<Integer>();
			Map<String,Object> bc = spoutDeclarer.getSpout().getComponentConfiguration();
			Config spoutConf = new Config();
			spoutConf.putAll(bc);
			spoutConfs.put(spoutId, spoutConf);
			for(int i=0;i<spoutDeclarer.getNumTasks();i++) {
				taskIds.add(i);
			}
			int numAllocated=0;
			for(int i=0;i<spoutDeclarer.getNumTasks();i++) {
				boolean local = !(dragonTopology.getReverseEmbedding()!=null &&
						!dragonTopology.getReverseEmbedding().contains(node.getComms().getMyNodeDescriptor(),spoutId,i));
				try {
					if(local) numAllocated++;
					Spout spout=(Spout) spoutDeclarer.getSpout().clone();
					if(local) hm.put(i, spout);
					OutputFieldsDeclarer declarer = new OutputFieldsDeclarer();
					spout.declareOutputFields(declarer);
					spout.setOutputFieldsDeclarer(declarer);
					TopologyContext context = new TopologyContext(spoutId,i,taskIds);
					spout.setTopologyContext(context);
					spout.setLocalCluster(this);
					SpoutOutputCollector collector = new SpoutOutputCollector(this,spout);
					spout.setOutputCollector(collector);
					if(local) {
						if(start) {
							spout.open(conf, context, collector);
						} else {
							openLater(spout,context,collector);
						}
					}
				} catch (CloneNotSupportedException e) {
					setShouldTerminate("could not clone object: "+e.toString());
				}
			}
			// TODO: make use of parallelism hint from topology to possibly reduce threads
			totalParallelismHint+=Math.ceil((double)numAllocated*spoutDeclarer.getParallelismHint()/spoutDeclarer.getNumTasks());
		}
		
		// allocate bolts and prepare them
		bolts = new HashMap<String,HashMap<Integer,Bolt>>();
		boltConfs = new HashMap<String,Config>();
		for(String boltId : dragonTopology.getBoltMap().keySet()) {
			if(dragonTopology.getReverseEmbedding()!=null &&
					!dragonTopology.getReverseEmbedding().contains(node.getComms().getMyNodeDescriptor(),boltId)) continue;
			log.debug("allocating bolt ["+boltId+"]");
			bolts.put(boltId, new HashMap<Integer,Bolt>());
			HashMap<Integer,Bolt> hm = bolts.get(boltId);
			BoltDeclarer boltDeclarer = dragonTopology.getBoltMap().get(boltId);
			Map<String,Object> bc = boltDeclarer.getBolt().getComponentConfiguration();
			Config boltConf = new Config();
			boltConf.putAll(bc);
			boltConfs.put(boltId, boltConf);
			ArrayList<Integer> taskIds=new ArrayList<Integer>();
			for(int i=0;i<boltDeclarer.getNumTasks();i++) {
				taskIds.add(i);
			}
			int numAllocated=0;
			for(int i=0;i<boltDeclarer.getNumTasks();i++) {
				boolean local = !(dragonTopology.getReverseEmbedding()!=null &&
						!dragonTopology.getReverseEmbedding().contains(node.getComms().getMyNodeDescriptor(),boltId,i));
				try {
					if(local) numAllocated++;
					Bolt bolt=(Bolt) boltDeclarer.getBolt().clone();
					if(local) hm.put(i, bolt);
					OutputFieldsDeclarer declarer = new OutputFieldsDeclarer();
					bolt.declareOutputFields(declarer);
					bolt.setOutputFieldsDeclarer(declarer);
					TopologyContext context = new TopologyContext(boltId,i,taskIds);
					bolt.setTopologyContext(context);
					InputCollector inputCollector = new InputCollector(this, bolt);
					bolt.setInputCollector(inputCollector);
					bolt.setLocalCluster(this);
					OutputCollector collector = new OutputCollector(this,bolt);
					bolt.setOutputCollector(collector);
					if(local) {
						if(start) {
							bolt.prepare(conf, context, collector);
						} else {
							prepareLater(bolt,context,collector);
						}
					}
				} catch (CloneNotSupportedException e) {
					log.error("could not clone object: "+e.toString());
				}
			}
			totalParallelismHint+=Math.ceil((double)numAllocated*boltDeclarer.getParallelismHint()/boltDeclarer.getNumTasks());
		}
		
		// prepare groupings
		for(String fromComponentId : dragonTopology.getSpoutMap().keySet()) {
			log.debug("preparing groupings for spout["+fromComponentId+"]");
			HashMap<String,StreamMap> 
				fromComponent = dragonTopology.getDestComponentMap(fromComponentId);
			if(fromComponent==null) {
				throw new RuntimeException("spout ["+fromComponentId+"] has no components listening to it");
			}
			log.debug(fromComponent);
			for(String toComponentId : fromComponent.keySet()) {
				HashMap<String,GroupingsSet> 
					streams = dragonTopology.getStreamMap(fromComponentId, toComponentId);
				BoltDeclarer boltDeclarer = dragonTopology.getBoltMap().get(toComponentId);
				ArrayList<Integer> taskIds=new ArrayList<Integer>();
				for(int i=0;i<boltDeclarer.getNumTasks();i++) {
					taskIds.add(i);
				}
				for(String streamId : streams.keySet()) {
					GroupingsSet groupings = dragonTopology.getGroupingsSet(fromComponentId, toComponentId, streamId);
					for(AbstractGrouping grouping : groupings) {
						grouping.prepare(null, null, taskIds);
					}
				}
			}
			
		}
		
		for(String fromComponentId : dragonTopology.getBoltMap().keySet()) {
			log.debug("preparing groupings for bolt["+fromComponentId+"]");
			HashMap<String,StreamMap> 
				fromComponent = dragonTopology.getDestComponentMap(fromComponentId);
			if(fromComponent==null) {
				log.debug(fromComponentId+" is a sink");
				continue;
			}
			log.debug(fromComponent);
			for(String toComponentId : fromComponent.keySet()) {
				HashMap<String,GroupingsSet> 
					streams = dragonTopology.getStreamMap(fromComponentId, toComponentId);
				BoltDeclarer boltDeclarer = dragonTopology.getBoltMap().get(toComponentId);
				ArrayList<Integer> taskIds=new ArrayList<Integer>();
				for(int i=0;i<boltDeclarer.getNumTasks();i++) {
					taskIds.add(i);
				}
				for(String streamId : streams.keySet()) {
					GroupingsSet groupings = dragonTopology.getGroupingsSet(fromComponentId, toComponentId, streamId);
					for(AbstractGrouping grouping : groupings) {
						grouping.prepare(null, null, taskIds);
					}
				}
			}
			
		}
		
		boltTickCount = new HashMap<String,Integer>();
		for(String boltId : bolts.keySet()) {
			if(boltConfs.get(boltId).containsKey(Config.TOPOLOGY_TICK_TUPLE_FREQ_SECS)) {
				boltTickCount.put(boltId, (Integer)boltConfs.get(boltId).get(Config.TOPOLOGY_TICK_TUPLE_FREQ_SECS));
			}
		}
		
		tickCounterThread = new Thread() {
			public void run() {
				this.setName("tick counter");
				while(!shouldTerminate && !isInterrupted()) {
					if(tickCounterTime==tickTime) {
						synchronized(tickCounterThread){
							try {
								wait();
							} catch (InterruptedException e) {
								log.info("tick counter thread exiting");
								break;
							}
						}
					}
					while(tickCounterTime<tickTime) {
						for(String boltId:boltTickCount.keySet()) {
							Integer count = boltTickCount.get(boltId);
							count--;
							if(count==0) {
								issueTickTuple(boltId);
								count=(Integer)boltConfs.get(boltId).get(Config.TOPOLOGY_TICK_TUPLE_FREQ_SECS);
							}
							boltTickCount.put(boltId, count);
						}
						tickCounterTime++;
					}
				}
			}
		};
		tickCounterThread.start();
		
		tickThread = new Thread() {
			public void run() {
				this.setName("tick");
				while(!shouldTerminate && !isInterrupted()) {
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						log.info("terminating tick thread");
						break;
					}
					tickTime++;
					synchronized(tickCounterThread) {
						tickCounterThread.notify();
					}
				}
			}
		};
		tickThread.start();


		outputsScheduler();
		
		
		//componentExecutorService = (ThreadPoolExecutor) Executors.newFixedThreadPool((Integer)totalParallelismHint);
		componentExecutorThreads = new ArrayList<Thread>();
		if(start) {
			scheduleSpouts();
			
		}
		

		
		
		
	}
	
	private void scheduleSpouts() {
		log.debug("scheduling spouts to run");
		for(String componentId : spouts.keySet()) {
			HashMap<Integer,Spout> component = spouts.get(componentId);
			for(Integer taskId : component.keySet()) {
				Spout spout = component.get(taskId);
				componentPending(spout);
			}
		}
		runComponentThreads();
	}
	
	private void prepareLater(Bolt bolt, TopologyContext context, OutputCollector collector) {
		boltPrepareList.add(new BoltPrepare(bolt,context,collector));
	}
	
	private void openLater(Spout spout, TopologyContext context, SpoutOutputCollector collector) {
		spoutOpenList.add(new SpoutOpen(spout,context,collector));
	}
	
	public void openAll() {
		log.info("opening bolts");
		for(BoltPrepare boltPrepare : boltPrepareList) {
			//log.debug("calling prepare on bolt "+boltPrepare+" with collector "+boltPrepare.collector);
			boltPrepare.bolt.prepare(conf, boltPrepare.context, boltPrepare.collector);
		}
		for(SpoutOpen spoutOpen : spoutOpenList) {
			//log.debug("calling open on spout "+spoutOpen.spout+" with collector "+spoutOpen.collector);
			spoutOpen.spout.open(conf, spoutOpen.context, spoutOpen.collector);
		}
		log.debug("scheduling spouts");
		scheduleSpouts();
	}

	private void issueTickTuple(String boltId) {
		Tuple tuple=RecycleStation.getInstance().getTupleRecycler(tickFields.getFieldNamesAsString()).newObject();;
		tuple.setValues(new Values("0"));
		tuple.setSourceComponent(Constants.SYSTEM_COMPONENT_ID);
		tuple.setSourceStreamId(Constants.SYSTEM_TICK_STREAM_ID);
		for(Bolt bolt : bolts.get(boltId).values()) {
			synchronized(bolt) {
				RecycleStation.getInstance().getTupleRecycler(tickFields.getFieldNamesAsString()).shareRecyclable(tuple, 1);
				bolt.setTickTuple(tuple);
				componentPending(bolt);
			}
		}
		RecycleStation.getInstance().getTupleRecycler(tickFields.getFieldNamesAsString()).crushRecyclable(tuple, 1);
	}
	
	private void checkCloseCondition() {
		log.debug("starting shutdown thread");
		Thread shutdownThread = new Thread() {
			@Override
			public void run() {
				for(String componentId : spouts.keySet()) {
					HashMap<Integer,Spout> component = spouts.get(componentId);
					for(Integer taskId : component.keySet()) {
						Spout spout = component.get(taskId);
						componentPending(spout);
					}
				}
				while(true) {
					boolean alldone=true;
					for(HashMap<Integer,Bolt> boltInstances : bolts.values()) {
						for(Bolt bolt : boltInstances.values()) {
							if(!bolt.isClosed()) {
								alldone=false;
								break;
							}
						}
						if(alldone==false) {
							break;
						}
					}
					if(alldone) break;
					log.info("waiting for work to complete...");
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						log.warn("interrupted while waiting for work to complete");
						break;
					}
				}
				log.debug("interrupting all threads");
				for(int i=0;i<totalParallelismHint;i++) {
					componentExecutorThreads.get(i).interrupt();
				}
				for(int i=0;i<conf.getDragonLocalclusterThreads();i++) {
					networkExecutorThreads.get(i).interrupt();
				}
				
				
				// call close on bolts
//				for(HashMap<Integer,Bolt> bolts : bolts.values()) {
//					for(Bolt bolt : bolts.values()) {
//						bolt.close();
//					}
//				}
				
				try {
					for(Thread thread : componentExecutorThreads) {
						thread.join();
					}
					for(Thread thread : networkExecutorThreads) {
						thread.join();
					}
				} catch (InterruptedException e) {
					log.warn("threads may not have terminated");
				}
				
				tickThread.interrupt();
				tickCounterThread.interrupt();
				
				try {
					tickThread.join();
					tickCounterThread.join();
				} catch (InterruptedException e) {
					log.warn("interrupted while waiting for tick thread");
				}
				
				// the local cluster can now be garbage collected
				synchronized(groupOperations) {
					for(GroupOperation go : groupOperations.get(TerminateTopologyGroupOperation.class)) {
						node.localClusterTerminated(topologyName,(TerminateTopologyGroupOperation) go);
					}
				}
				
			}
		};
		shutdownThread.setName("shutdown");
		shutdownThread.start();
	}
	
	private void runComponentThreads() {
		log.info("starting component executor threads with "+totalParallelismHint+" threads");
		for(int i=0;i<totalParallelismHint;i++){
			//final int thread_id=i;
			componentExecutorThreads.add(new Thread(){
				@Override
				public void run(){
					Component component;
					while(true){
						try {
							component = componentsPending.take();
						} catch (InterruptedException e) {
							log.info("component thread interrupted");
							break;
						}
						// TODO:the synchronization can be switched if the component's
						// execute/nextTuple is thread safe
						synchronized(component) {
							if(shouldTerminate && component instanceof Spout) {
								if(component.isClosed()) continue;
								component.setClosing();
								// a closed component will not reschedule itself
							}
							component.run();
						}	
					}
				}
			});
			componentExecutorThreads.get(i).setName("component executor "+i);
			componentExecutorThreads.get(i).start();
		}
	}

	
	private void outputsScheduler(){
		log.debug("starting the outputs scheduler with "+conf.getDragonLocalclusterThreads()+" threads");
		for(int i=0;i<conf.getDragonLocalclusterThreads();i++) {
			networkExecutorThreads.add(new Thread() {
				@Override
				public void run(){
					HashSet<Integer> doneTaskIds=new HashSet<Integer>();
					NetworkTaskBuffer queue;
					while(true) {
						try {
							queue = outputsPending.take();
						} catch (InterruptedException e) {
							log.info("network thread interrupted");
							break;
						}
						synchronized(queue.lock) {
							//if(shouldTerminate) System.out.println("count "+queue.size());
							NetworkTask networkTask = (NetworkTask) queue.peek();
							if(networkTask!=null) {
								//System.out.println("processing task");
								Tuple tuple = networkTask.getTuple();
								String name = networkTask.getComponentId();
								doneTaskIds.clear();
								for(Integer taskId : networkTask.getTaskIds()) {
									RecycleStation.getInstance()
									.getTupleRecycler(tuple.getFields()
											.getFieldNamesAsString()).shareRecyclable(tuple,1);
									if(bolts.get(name).get(taskId).getInputCollector().getQueue().offer(tuple)){
										doneTaskIds.add(taskId);
										componentPending(bolts.get(name).get(taskId));
									} else {
										RecycleStation.getInstance()
										.getTupleRecycler(tuple.getFields()
												.getFieldNamesAsString()).crushRecyclable(tuple,1);
										//log.debug("blocked");
									}
								}
								networkTask.getTaskIds().removeAll(doneTaskIds);
								if(networkTask.getTaskIds().size()==0) {
									queue.poll();
									RecycleStation.getInstance().getNetworkTaskRecycler().crushRecyclable(networkTask, 1);
								} else {
									outputPending(queue);
								}
							} else {
								log.error("queue empty!");
							}	
						}
					}
				}
			});
			networkExecutorThreads.get(i).setName("network executor "+i);
			networkExecutorThreads.get(i).start();
		}
		
	}
	
	public void componentPending(final Component component){
		try {
			componentsPending.put(component);
		} catch (InterruptedException e){
			log.error("interrupted while adding component pending ["+component.getComponentId()+":"+component.getTaskId()+"]");
		}
	}
	
	public void outputPending(final NetworkTaskBuffer queue) {
		try {
			outputsPending.put(queue);
			//log.debug("outputPending pending "+outputsPending.size());
		} catch (InterruptedException e) {
			log.error("interrupted while adding output pending");
		}
	}
	
	public String getTopologyId() {
		return topologyName;
	}
	
	public Config getConf(){
		return conf;
	}
	
	public DragonTopology getTopology() {
		return dragonTopology;
	}

	public void setShouldTerminate() {
		shouldTerminate=true;
		checkCloseCondition();
	}
	
	public void setShouldTerminate(String error) {
		setShouldTerminate();
		throw new RuntimeException(error);
	}
	
	public HashMap<String,HashMap<Integer,Bolt>> getBolts(){
		return bolts;
	}
	
	public HashMap<String,HashMap<Integer,Spout>> getSpouts(){
		return spouts;
	}
	
	public Node getNode(){
		return node;
	}
	
	public void setGroupOperation(GroupOperation go) {
		synchronized(groupOperations) {
			if(!groupOperations.containsKey(go.getClass())) {
				groupOperations.put(go.getClass(), new HashSet<GroupOperation>());
			}
			HashSet<GroupOperation> gos = groupOperations.get(go.getClass());
			gos.add(go);
		}
	}

}
