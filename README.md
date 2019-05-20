# TODO
use .yaml as the configuration file type.

# loopAnalysis
Implemented by wala

We target the time comsuming loop, and make a static analysis.



usage:

```
Compile - 
ant compile-sa(-xxx)
ant jar-sa(-xxx)
```


| bug ID | location                                                     | comment  |
| ------ | ------------------------------------------------------------ | -------- |
| ca6744 | org.apache.cassandra.db.index.SecondaryIndexManager #328?    | no lock  |
| ha4584 | FSDataset.FSVolumeSet #519 #526                              | not lock |
| hb3483 | MemStoreFlusher #302                                         |          |
| hd5153 | namenode/NameNodeRpcServer.java #981<br>blockmanagement/BlockManager.java #1775/1849 |          |
| mr2705 | org.apache.hadoop.mapred.TaskTracker.TaskLauncher #2239      |          |
| mr4088 | org.apache.hadoop.mapred.TaskTracker #423?                   | no lock? |
| mr4576 | org.apache.hadoop.io #69                                     |          |
| mr4813 | org/apache/hadoop/mapreduce/lib/output/FileOutputCommitter.java #302 |          |



# ca6744

**Fix:**

[Alexey Plotnik](https://issues.apache.org/jira/secure/ViewProfile.jspa?name=aplotnik) Yes. concurrent_compactors = 1 means compaction executor which rebuilding secondary index or cleanup is run is limited to single thread.

[CASSANDRA-6503](https://issues.apache.org/jira/browse/CASSANDRA-6503) in 2.0.x pushed streaming finalize process including secondary index rebuild to separate thread so it won't block network transfer.

Stream session itself completes until index rebuild is done though.



**Node 1 - real data sender**

*org.apache.cassandra.streaming.FileStreamTask*

```java
protected void receiveReply() throws IOException{
    MessagingService.validateMagic(input.readInt()); /*193*///#stuck here, waitting for data
    String id = input.readUTF();
    ...
    handler.doVerb(message, id);
}
```

*org.apache.cassandra.streaming.StreamReplyVerbHandler*

```java
public void doVerb(MessageIn<StreamReply> message, String id){/*MsgProc*/}
```

**Node 2- real data receiver**

*org.apache.cassandra.streaming.StreamInSession*

```java
public void closeIfFinished() throws IOException{
    entry.getKey().addSSTables(entry.getValue());   //#waiting things inside
    entry.getKey().indexManager.maybeBuildSecondaryIndexes(entry.getValue(), entry.getKey().indexManager.allIndexesNames()); 
    // send reply to source that we're done
    OutboundTcpConnection.write(reply.createMessage(),//#cannot reach here, blocked here by above time-consuming work,
                                sessionId.toString(),//#couldn't send message to node 1
                                System.currentTimeMillis(),
                                new DataOutputStream(socket.getOutputStream()),
                                MessagingService.instance().getVersion(getHost()));    
}
```

*org.apache.cassandra.db.index.SecondaryIndexManager*

```java
public void maybeBuildSecondaryIndexes(...){
    Future<?> future = CompactionManager.instance.submitIndexBuild(builder);
    future.get();/*144*/	//#waiting other works like "cleanup" to finish, then handle this future   #block here, waiting for other works, like cleanup
    flushIndexesBlocking();
}

public void  flushIndexesBlocking(){
	for (SecondaryIndex index : indexesByColumn.values())/*328*///#loop? maybe there
		index.forceBlockingFlush();   -> ... ->  another future.get()
}
```



# ha4584

not lock, **2 operation in one thread**

FSVolumeset#getBlockInfo() <lock> <u>data.getBlockReport() --/-- namenode.sendHeartBeat()</u>

![1544102634263](assets/1544102634263.png)



```java
515 	// # New Discovery: large-loop affect the hot lock 'FSVolumSet.sync'
517 	synchronized long getRemaining() throws IOException { //#O(N)*O(P), maybe smaller than #getBlockInfo                                                                                                                                   
518   		long remaining = 0L;          	//#fixed(delete sync, use a new diff small lock 'statsLock' in caller) also in this bug's patch
519   		for (int idx = 0; idx < volumes.length; idx++) {
520     		remaining += volumes[idx].getAvailable();     	//#call "DU" and "DF", O(P), P = #blocks on a volume
521   		}                                                   //#
522   		return remaining;
523 	}                                                           //# this is the source problem
525 	synchronized void getBlockInfo(TreeSet<Block> blockSet) { //# time-consuming, O(N=2)*O(P),  P=#blocks on a volume
526   		for (int idx = 0; idx < volumes.length; idx++) {    	
527     		volumes[idx].getBlockInfo(blockSet);
528   		}                                               //#no bug, no fixed

```



# hb3483



![1544112555638](assets/1544112555638.png)

![1544112607394](assets/1544112607394.png)

*MemStoreFlusher* 302

```java
289 public synchronized void reclaimMemStoreMemory() {
290	if (server.getGlobalMemStoreSize() >= globalMemStoreLimit) {
291  	flushSomeRegions();
292	}
293  }
298  private synchronized void flushSomeRegions() {
299	// keep flushing until we hit the low water mark
300	long globalMemStoreSize = -1;
301	ArrayList<HRegion> regionsToCompact = new ArrayList<HRegion>();
302	for (SortedMap<Long, HRegion> m =
303        this.server.getCopyOfOnlineRegionsSortedBySize();
304      (globalMemStoreSize = server.getGlobalMemStoreSize()) >= 	#O(N), N may be not big, N = #regions to flush
305        this.globalMemStoreLimitLowMark;) {
306  	// flush the region with the biggest memstore
307  	if (m.size() <= 0) {
308    	LOG.info( .. );
313    	break;
314  	}
315  	HRegion biggestMemStoreRegion = m.remove(m.firstKey()); #end. Are all basic Java operations like SortedMap#remove cheap?
316  	LOG.info( .. );                                     	#how to define? Except for RPC calls and I/O etc?
322  	if (!flushRegion(biggestMemStoreRegion, true)) {   # K, expensive, K = O(P)*O(Q)*I/O..   both P and Q are small
323    	LOG.warn("Flush failed");
324    	break;
325  	}
326      regionsToCompact.add(biggestMemStoreRegion);
327	}
328	for (HRegion region : regionsToCompact) {                          #O(N), but cheap operations
329      server.compactSplitThread.requestCompaction(region, getName());  #can end, "(can) end" means cheap
330	}
331  }
```





# hd5153

 **O(M)** *** O(N) \* O(P) * ?** 

(no time consuming op)

Paths: 

```
NameNodeRpcServer#blockReport(StorageBlockReport[] reports) 
-> O(M) * BlockManager#processReport(writeLock)
```

*org/apache/hadoop/hdfs/server/namenode/NameNodeRpcServer.java* 

```java
public DatanodeCommand blockReport(...) {	/*973*/   
	for(StorageBlockReport r : reports) /*981*/ {processReport(...);} //#jx: it's 2 in my default settings     #O(M) C.S 	M = #volumes for the DataNode 
}
```

*org/apache/hadoop/hdfs/server/blockmanagement/BlockManager.java*

```java
public void processReport(...){/*1625*/
    namesystem.writeLock();/*1628*/
    //processFirstBlockReport(node, storage.getStorageID(), newReport);	
    //or
    //processReport(node, storage, newReport);                         
    namesystem.writeUnlock();/*1670*/
}

private void processFirstBlockReport(...){/*1767*/
    while(itBR.hasNext())/*1775*/{//#the bug loop,could be large  #O(N),  N=#blocks on a volume
        // #O(P)*...
        // If block does not belong to any file, we are done.
        // If block is corrupt, mark it and continue to next block.
        // If block is under construction, add this replica to its list
        //add replica if appropriate
    }
}

private void processReport(...){/*1714*/
    reportDiff(...);
    //#O(N),  N=#blocks on a volume=P1+P2+P3+...
}

private void reportDiff(...){/*1827*/
    while(itBR.hasNext())/*1849*/{...} //#the bug loop,could be large  #O(N)
}
```



# mr2705

Critical Section: single thread + **wait for one**

*org.apache.hadoop.mapred.TaskTracker.TaskLauncher* 

```java
public void More ...run() {
 	while (!Thread.interrupted()) {/*2239*/
    	try {
            TaskInProgress tip;
            Task task;
            synchronized (tasksToLaunch) {
                while (tasksToLaunch.isEmpty()) {tasksToLaunch.wait();}
                //get the TIP
                tip = tasksToLaunch.remove(0); //#Queue - a List type actually
                task = tip.getTask();
                ...
           	}
            ...
      	//got a free slot. launch the task
//JX-instrument-EventHandler-BEGIN
      		startNewTask(tip);                //#handled by the thread itself
//JX-instrument-EventHandler-END
    	} ...
    }
}
```

*org.apache.hadoop.mapred.TaskTracker*

```java
void startNewTask(TaskInProgress tip) {
    RunningJob rjob = localizeJob(tip); /*2335*/ //#include a file-copy LOOP
}

RunningJob localizeJob(TaskInProgress tip){
 	synchronized (rjob) {
        if (!rjob.localized) {    	
            JobConf localJobConf = localizeJobFiles(t, rjob);
        }
    }
}

JobConf localizeJobFiles(Task t, RunningJob rjob){
    localizeJobJarFile(userName, jobId, localFs, localJobConf);
}

private void localizeJobJarFile(...){
    // Download job.jar
    fs.copyToLocalFile(jarFilePath, localJarFile);
}
```



# mr4088

(this is not a lock-related bug ????)

 

*org.apache.hadoop.mapred.TaskTracker*

```java
417private Thread taskCleanupThread =
	new Thread(new Runnable() {
    	public void run() {
      	while (true) {
        	try {
422       	TaskTrackerAction action = tasksToCleanup.take();  #QUEUE - BlockingQueue
//JX-instrument-EventHandler-BEGIN
423       	checkJobStatusAndWait(action);
//JX-instrument-EventHandler-END
424       	if (action instanceof KillJobAction) {
            	purgeJob((KillJobAction) action);
          	} else if (action instanceof KillTaskAction) {
                processKillTaskAction((KillTaskAction) action);
          	} else {
            	LOG.error("Non-delete action given to cleanup thread: "
                      	+ action);
          	}
JX-instrument-EventHandler-END  jx: couldn't insert successfully
432     	} catch (Throwable except) {
              LOG.warn(StringUtils.stringifyException(except));
        	}
      	}
    	}
  	}, "taskCleanup");
 
  private void checkJobStatusAndWait(TaskTrackerAction action)
  throws InterruptedException {
	JobID jobId = null;org.apache.hadoop.io
	if (action instanceof KillJobAction) {
  	jobId = ((KillJobAction)action).getJobID();
	} else if (action instanceof KillTaskAction) {
  	jobId = ((KillTaskAction)action).getTaskID().getJobID();
	} else {
  	return;
	}
	RunningJob rjob = null;
	synchronized (runningJobs) {
  	rjob = runningJobs.get(jobId);
	}
	if (rjob != null) {
  	synchronized (rjob) {    	//#this bug is caused by single handler thread, NOT lock, so even has "rjob.wait", won't affect
    	while (rjob.localizing) {  //#LOOP
      	rjob.wait();
    	}
  	}
	}
}
```



# mr4576

*Downloading **thread*** holds <u>dist cache lock</u> which blocks the *dist cache delete **thread*** which holds the <u>full dist cache map lock</u> that blocks the *job cleanup **thread*** that holds that <u>Task Tracker lock</u> which blocks the *heartbeat **thread***.

*org.apache.hadoop.io*

```java
public static void copyBytes(...)/*63*/{
    ...
	while (bytesRead >= 0) {/*69*///   #O(P) * I/O  	 jx: this 'while' is the problem, the iteration can be large
    	out.write(buf, 0, bytesRead);// #java.io.PrintStream(extends FilterOutputStream extends OutputStream)
        ...
  	}
}
```



# mr4813

**O(N)** *** K = O(P)recursive \* RPC !**



*/hadoop-0.23.3-src/hadoop-mapreduce-project/hadoop-mapreduce-client/hadoop-mapreduce-client-core/src/main/java/org/apache/hadoop/mapreduce/lib/output/FileOutputCommitter.java*            #Not easy to find

```java
298  public void commitJob(JobContext context) throws IOException {
299	if (hasOutputPath()) {
300  	Path finalOutput = getOutputPath();
301  	FileSystem fs = finalOutput.getFileSystem(context.getConfiguration());
302  	for(FileStatus stat: getAllCommittedTaskPaths(context)) {	try { LOG.info("JX - sleep ..."); Thread.sleep(50000); LOG.info("JX - wakeup"); } catch (InterruptedException e) {}      	#O(N)                # large- iteration for-loop
303    	mergePaths(fs, stat, finalOutput);                               	# K
304  	}

```



# zk1049(closing connection delay hearbeat)



# zk1504(core thread connection)



# zk1505(core thread event)



# zk1642(dup read zxid)



# ca2178(executor pool queue type limit thread scale)



# ca2253(file deletion delay heartbeat)



# ca3261(Thrift useless Trans.)



# ca4761(send res one by one)



# ca5175(connection thread not exit)(thread leak)



# ca5884(copy mem one by one)
