package sa.tcop;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSACFG;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.types.ClassLoaderReference;

import javafx.util.Pair;
import sa.lock.LockAnalyzer;
import sa.lock.LockInfo;
import sa.lock.LoopingLockInfo;
import sa.lockloop.CGNodeInfo;
import sa.lockloop.CGNodeList;
import sa.lockloop.InstructionInfo;
import sa.loop.LoopAnalyzer;
import sa.loop.LoopInfo;
import sa.loop.TCOpUtil;
import sa.loop.TcOperationInfo;
import sa.wala.CGNodeUtil;
import sa.wala.IRUtil;
import sa.wala.WalaAnalyzer;

public class LockingLoopAnalyzer {
	
	// wala
	WalaAnalyzer walaAnalyzer;
	CallGraph cg;
	Path outputDir;
	// database
	CGNodeList cgNodeList = null;
	// lock & loop
	LockAnalyzer lockAnalyzer;
	LoopAnalyzer loopAnalyzer;

	HashSet<LoopInfo> loops2test = new HashSet<LoopInfo>();   //entry in CGNodeList
	
	//tmp - for test
	String functionname_for_test = "method signature";
	
	TCOpUtil iolooputil = null;
	
	
	
	
	public LockingLoopAnalyzer(WalaAnalyzer walaAnalyzer, LockAnalyzer lockAnalyzer, LoopAnalyzer loopAnalyzer, CGNodeList cgNodeList) {
		this.walaAnalyzer = walaAnalyzer;
		this.cg = this.walaAnalyzer.getCallGraph();
		this.outputDir = this.walaAnalyzer.getTargetDirPath();
		this.lockAnalyzer = lockAnalyzer;
		this.loopAnalyzer = loopAnalyzer;
		this.cgNodeList = cgNodeList;
		//others
		this.iolooputil = new TCOpUtil( this.walaAnalyzer.getTargetDirPath() );
		this.setTCLockingLoops = new HashSet<LoopInfo>();
		this.setTCLockingLoopsCircle = new HashSet<LoopInfo>();
	}
	
	// Please call doWork() manually
	public void doWork() {
		System.out.println("\nJX - INFO - LockingLoopAnalyzer: doWork...");
		findLoopsForAllLocks();
		//statistic();
		//printResultStatus();
		//analysisPath();
		final_statistic();
	}
	
	
	public int getNLoopingLocks() {
		return this.nLoopingLocks;
	}
	
	public int getNLoopingLockingCGNodes() {
		return this.nLoopingLockingCGNodes;
	}
		
	public HashSet<LoopInfo> getTestLoops(){
		return this.loops2test;
	}
	
	
	
	
	/*****************************************************************************************************
	 * Find inside loops for every Lock
	 *****************************************************************************************************/
	
	private void findLoopsForAllLocks() {
		System.out.println("JX - INFO - LockingLoopAnalyzer: findLoopsForAllLocks");
		
		// Initialization by DFS for lock-containing CGNodes
		//for (CGNodeInfo cgNodeInfo: lockAnalyzer.getLockCGNodes()) {
			//System.err.println(cg.getNode(id).getMethod().getSignature());
			//dfsToGetFunctionInfos( cgNodeInfo.getCGNode(), 0 );
		//}
		
		// Find loops for every lock for lock-containing CGNodes. For safety, can't combine with the above, because this may modify value in CGNodeInfo for eventual usage.
		for (CGNodeInfo cgNodeInfo: lockAnalyzer.getLockCGNodes()) {
			if(cgNodeInfo.hasLoopingLocks)
				for(LoopingLockInfo loopingLock: cgNodeInfo.looping_locks.values())
					findLoopsForLock(loopingLock);
		}
		
	}
	  
	/**
	* for single loop
	*/
	public void findLoopsForLock(LoopingLockInfo loopingLock) {

		LockInfo lock = loopingLock.getLock();
		CGNode cgNode = loopingLock.getCGNode();
		int id = cgNode.getGraphNodeId();
		IR ir = cgNode.getIR();
		SSACFG cfg = ir.getControlFlowGraph();
		SSAInstruction[] instructions = ir.getInstructions();
	
		CGNodeInfo cgNodeInfo = cgNodeList.get(id); 
	
		/* already done at LoopInfo initializtion
		loop.numOfTcOperations_recusively = 0;
		loop.tcOperations_recusively = new ArrayList<SSAInstruction>();
		 */
		BitSet traversednodes = new BitSet();
		traversednodes.clear();
		traversednodes.set( id );
		

		int begin_size = loopingLock.getLoopPaths().size();//maybe unnecessary-> begin_size = 0
		if(begin_size!=0)
			System.err.println("begin_size!=0");
		List<Pair<CGNode,PathEntry>> circle_target_Src = new ArrayList<Pair<CGNode,PathEntry>>();
	
		//add loops in the first level
		for(LoopInfo loop:loopingLock.getLoops()) {
			//SSAInstruction last_ssa = instructions[cfg.getBasicBlock(loop.getEndBasicBlockNumber()).getFirstInstructionIndex()];
			SSAInstruction first_ssa = cfg.getBasicBlock(loop.getBeginBasicBlockNumber()).getLastInstruction();
			PathInfo path = new PathInfo();
			PathEntry pe = new PathEntry(cgNodeInfo,first_ssa);
			//pe.delLoop(loop); //remove current loop in path entry
			path.addPathEntry(pe);
			path.setFunction(cgNode);
			loopingLock.getLoopPaths().add(new Pair<>(loop,path));		
		}
				
				
	
		for (int bbnum: lock.getBasicBlockNumbers()) {
			int first_index = cfg.getBasicBlock(bbnum).getFirstInstructionIndex();
			int last_index = cfg.getBasicBlock(bbnum).getLastInstructionIndex();
			for (int i = first_index; i <= last_index; i++) {
				SSAInstruction ssa = instructions[i];
				if (ssa == null) continue;
				
				if ( cgNodeInfo.tcOperations.contains( ssa ) ) {
					continue;
				}
				// filter the rest I/Os
				if ( iolooputil.isJavaIOSSA(ssa) )
					continue;
				
				if (ssa instanceof SSAInvokeInstruction) {  
					SSAInvokeInstruction invokessa = (SSAInvokeInstruction) ssa;
					java.util.Set<CGNode> set = cg.getPossibleTargets(cgNode, invokessa.getCallSite());
					for (CGNode sub_cgnode: set) {						
						if (sub_cgnode.equals(cgNode)) {//self call mark
							System.err.println("L1 Self-call!! "+cgNode.getMethod().toString());
							circle_target_Src.add(new Pair<>(sub_cgnode,new PathEntry(cgNodeInfo,ssa)));
							continue;
						}
						PathInfo callpath = new PathInfo();	
						callpath.callpath.add(new PathEntry(cgNodeInfo,ssa));
						dfsToGetLoopsForSSA(sub_cgnode, 0, traversednodes, loopingLock,  callpath);
					}
				}
			}
		} //for-bbnum		
		//mark circle call in path node
		for(int i=begin_size;i<loopingLock.getLoopPaths().size();i++) {
			for(Pair<CGNode,PathEntry> p : circle_target_Src) {
				loopingLock.getLoopPaths().get(i).getValue().getPathNode(p.getKey()).addCircle(p.getValue());
			}
		}
		//there is no need to remove outer loop of lock, because it does not exist in LoopingLockInfo.inner_loops
	}
		
	
	public void dfsToGetLoopsForSSA(CGNode cgNode, int depth, BitSet traversednodes, LoopingLockInfo loopingLock, PathInfo callpath) {
		//jx: if want to add this, then for MapReduce we need to more hadoop-common&hdfs like "org.apache.hadoop.conf" not juse 'fs/security' for it
		/*
		if ( !walaAnalyzer.isInPackageScope(cgNode) )
			return ;
		*/ 
		 
		if ( !cgNode.getMethod().getDeclaringClass().getClassLoader().getReference().equals(ClassLoaderReference.Application) 
				|| cgNode.getMethod().isNative()) { // IMPO - native - must be
			return ;
		}
	
		int id = cgNode.getGraphNodeId();
		CGNodeInfo cgNodeInfo = cgNodeList.get(id);
		
			
		if ( traversednodes.get(id))
			return ;
		traversednodes.set(id);
	
		//test
		if (cgNodeInfo == null) {
			//System.err.println("ZC - error - function == null in LockingLoopAnalyzer.dfsToGetLoopsForSSA..." + cgNode.getMethod().toString());
			return;
		}    		
	
		IR ir = cgNode.getIR();  //if (ir == null) return;
		SSACFG cfg = ir.getControlFlowGraph();
		SSAInstruction[] instructions = ir.getInstructions();

		int begin_size = loopingLock.getLoopPaths().size();
		List<Pair<CGNode,PathEntry>> circle_target_Src = new ArrayList<Pair<CGNode,PathEntry>>();
	
		//add Loop
		if(cgNodeInfo.hasLoops())
			for (LoopInfo loop: cgNodeInfo.getLoops()) {
				SSAInstruction first_ssa = cfg.getBasicBlock(loop.getBeginBasicBlockNumber()).getLastInstruction();
				PathInfo path = new PathInfo(callpath);
				PathEntry pe = new PathEntry(cgNodeInfo,first_ssa);
				//pe.delLoop(loop); //remove current loop in path entry
				path.addPathEntry(pe);
				path.setFunction(cgNode);
				loopingLock.getLoopPaths().add(new Pair<>(loop,path));	
			}
		//end
		
		//if(callpath.containNode("flushSomeRegions"))
			//System.err.println(callpath);
			
		for (int i = 0; i < instructions.length; i++) {
			SSAInstruction ssa = instructions[i];
			if (ssa == null)
				continue;
			
			if ( cgNodeInfo.tcOperations.contains( ssa ) )
				;//continue;  //remove continue to enter inner tc op mr4576
			// filter the rest I/Os
			if ( iolooputil.isJavaIOSSA(ssa) )
				continue;
			if (ssa instanceof SSAInvokeInstruction) {  
				SSAInvokeInstruction invokessa = (SSAInvokeInstruction) ssa;				
				java.util.Set<CGNode> set = cg.getPossibleTargets(cgNode, invokessa.getCallSite());
				
				if(ssa.toString().indexOf("StoreFlusher, flushCache()")>=0)
					System.err.println(set.size());
				
				for (CGNode sub_cgnode: set) {
					if (sub_cgnode.equals(cgNode)) {//self call mark
						//System.err.println("Self-call!! "+cgNode.getMethod().toString()+" @ "+ssa);
						circle_target_Src.add(new Pair<>(sub_cgnode,new PathEntry(cgNodeInfo,ssa)));
						continue;
					}
					if (callpath.getPathNode(sub_cgnode) != null) {
						circle_target_Src.add(new Pair<>(sub_cgnode,new PathEntry(cgNodeInfo,ssa)));
						//System.err.println("Circle-call!! "+cgNode.getMethod().toString()+" @ "+ssa);
						continue;
					}
					//else do next level
					PathInfo curPathInfo = new PathInfo(callpath);	
					curPathInfo.callpath.add(new PathEntry(cgNodeInfo,ssa));				
					dfsToGetLoopsForSSA(sub_cgnode, depth+1, traversednodes, loopingLock, curPathInfo);
				}
			}
		}//for instructions

		//mark circle call in path node
		for(int i=begin_size;i<loopingLock.getLoopPaths().size();i++) {
			for(Pair<CGNode,PathEntry> p : circle_target_Src) {
				loopingLock.getLoopPaths().get(i).getValue().getPathNode(p.getKey()).addCircle(p.getValue());
			}
		}		
	}
	
	// results
		int nLocks = 0;
		int nLockingCGNodes = 0;
		int nLoopingLocks = 0;
		int nLoopingLockingCGNodes = 0;

		int nLockingLoops = 0;
		
		int nTCLocks = 0;
		int nTCLockingCGNodes = 0;
		int nTCLockingLoops = 0;
		
		Set<LoopInfo> setTCLockingLoops = null;	
		Set<LoopInfo> setTCLockingLoopsCircle = null;
		
		
		/*
		int nHeartbeatLocks = 0;
		int nHeartbeatLockGroups = 0;
		int nSuspectedHeavyLocks = 0; 
		*/
		
	public void statistic() {

		// summary
		boolean flag_tc_node = false;
		boolean flag_tc_lock = false;
		
		for (CGNodeInfo cgNodeInfo: lockAnalyzer.getLockCGNodes()) {
			this.nLockingCGNodes ++;
			this.nLocks += cgNodeInfo.getLocks()==null? 0:cgNodeInfo.getLocks().size();
			
			if(cgNodeInfo.hasLoopingLocks)
			{
				this.nLoopingLockingCGNodes++;
				this.nLoopingLocks += cgNodeInfo.looping_locks.values().size();
				for(LoopingLockInfo loopingLock: cgNodeInfo.looping_locks.values()) {
					this.nLockingLoops += loopingLock.getLoopPaths().size();
					for(Pair<LoopInfo,PathInfo> lp:loopingLock.getLoopPaths())	{
						if(lp.getKey().numOfTcOperations_recusively>0)
						{
							setTCLockingLoops.add(lp.getKey());
							this.nTCLockingLoops ++;
							flag_tc_node = true;
							flag_tc_lock = true;
							if(lp.getValue().getCircleNumInloop()>0)
								this.setTCLockingLoopsCircle.add(lp.getKey());
						}
					}
					if(flag_tc_lock)
						this.nTCLocks++;
				}//end looping lock for
				if(flag_tc_node)
					this.nTCLockingCGNodes++;
			}//end if
		}//end cgNodes for
		
	}
		  
    public void printResultStatus() {
    	System.out.println("ZC - INFO - LockingLoopAnalyzer: printResultStatus");
    	System.out.println("#Lock Nodes = " + this.nLockingCGNodes +" ( " + this.nLocks + " locks)");
    	System.out.println("#Looping Lock Nodes = " + this.nLoopingLockingCGNodes + " ( " + this.nLoopingLocks + " looping locks)");
    	System.out.println("	#Locking loop paths = " + this.nLockingLoops);
    	
    	System.out.println("#TC Lock Nodes = " +this.nTCLockingCGNodes+ " ( "+ this.nTCLocks +" TC Locks)");
    	
    	System.out.println("#TCLockingLoops Path = " + this.nTCLockingLoops + "(#loops = "+ this.setTCLockingLoops.size() +")");
    	System.out.println("#TCLockingLoops with circle = " + this.setTCLockingLoopsCircle.size() );
    }   
    
    
    public void analysisPath() {

		System.out.println("");
		System.out.println("ZC - INFO .........analysisPath........");
    	
		//travel loop nodes
    	int count = 0;
    	for(CGNodeInfo lcg:this.loopAnalyzer.getLoopCGNodes())
    		if(lcg.hasLoops())
    			for(LoopInfo loop:lcg.getLoops())
    				count++;//System.out.println(loop.getCGNode().getMethod());
		System.out.println("#loop in loopAnalyzer.getLoopCGNodes() = " + count);
		
		//travel TC loops
    	count = 0;
		for (CGNodeInfo cgNodeInfo: loopAnalyzer.getLoopCGNodes())
			for (LoopInfo loop: cgNodeInfo.getLoops())
				if (loop.numOfTcOperations_recusively > 0) {
					count++;
					//if(loop.getCGNode().getMethod().toString().indexOf("copy")>=0)
					//System.out.println( loop.getCGNode().getMethod().toString() + loop.tcOperations_recusively_info );
				}
		System.out.println("#loop in loopAnalyzer.getLoopCGNodes() with numOfTcOperations_recusively > 0 = " + count);
		
		//travel Locking TC loops
		count = 0;
    	for(LoopInfo loop:setTCLockingLoops)
    		count++;//System.err.println(loop.toString_get());
		System.out.println("#loop in setTCLockingLoops = " + count);
    	
		//search all lock-loop-tc path nodes
		for (CGNodeInfo cgNodeInfo: lockAnalyzer.getLockCGNodes()) {
			if(cgNodeInfo.hasLoopingLocks // set specific start function
					&& cgNodeInfo.getCGNode().getMethod().toString().indexOf("")>=0)
			{
				for(LoopingLockInfo loopingLock: cgNodeInfo.looping_locks.values()) {
					for(Pair<LoopInfo,PathInfo> lp:loopingLock.getLoopPaths())	{
						if(lp.getKey().numOfTcOperations_recusively>5 // set specific node name
								&&lp.getValue().containNode(" "))
						{
							//System.out.println(lp.getValue() );
							//System.out.println(lp.getKey() );
							
						}
					}
				}//end looping lock for
			}//end if
		}//end cgNodes for
    }
    

    
	public void final_statistic() {	

		System.out.println("ZC - INFO .........final analysis........");
		
		int nfunc = this.walaAnalyzer.getNPackageFuncs();
		int nfuncLock = this.lockAnalyzer.getNLockingCGNodes();
		int nfuncLoop = this.loopAnalyzer.getNLoopingCGNodes();		
		int nfuncLoopingLock = 0;
		int nfuncTcLoopingLock = 0;
		
		int nlock = this.lockAnalyzer.getNLocks();
		int nlockgroup = this.lockAnalyzer.getNLockGroups();
		int nlockLoop = 0;
		int nlockTcLoop = 0;
		
		int nloop = this.loopAnalyzer.getNLoops();
		int nloopLock = 0;
		int nloopTc = this.loopAnalyzer.getNTcLoops();
		int nloopTcLock = 0;
		

		Set<LoopInfo> setTCLockingLoops = new HashSet<LoopInfo>();
		Set<LoopInfo> setLockingLoops = new HashSet<LoopInfo>();
		

		Set<LoopingLockInfo> setTcLoopingLock = new HashSet<LoopingLockInfo>();
		Set<CGNodeInfo> setTcLoopingLockFuncs = new HashSet<CGNodeInfo>();

		Set<LoopingLockInfo> setLoopingLockMerge = new HashSet<LoopingLockInfo>();
		Set<CGNodeInfo> setTcLoopingLockFuncsMerge = new HashSet<CGNodeInfo>();
		
		

		Set<CGNodeInfo> setTc5Funcs = new HashSet<CGNodeInfo>();
		Set<CGNodeInfo> setTcCFuncs = new HashSet<CGNodeInfo>();
				
		//travel lock cgNodes
		for (CGNodeInfo cgNodeInfo: lockAnalyzer.getLockCGNodes()) {						
			if(cgNodeInfo.hasLoopingLocks && pruning(cgNodeInfo,this.walaAnalyzer.getTargetDirPath().toString()))
			{
				nfuncLoopingLock++;
				//travel looping lock info
				for(LoopingLockInfo loopingLock: cgNodeInfo.looping_locks.values()) 
				{										
					//this.nLockingLoops += loopingLock.getLoopPaths().size();
					nlockLoop ++;
					//traval all path
					for(Pair<LoopInfo,PathInfo> lp:loopingLock.getLoopPaths())	{
						setLockingLoops.add(lp.getKey());
						if(lp.getKey().numOfTcOperations_recusively>0)
						{							
							setTCLockingLoops.add(lp.getKey());
							this.nTCLockingLoops ++;
							setTcLoopingLockFuncs.add(cgNodeInfo);
							setTcLoopingLock.add(loopingLock);
							check(cgNodeInfo,loopingLock,lp.getKey(),lp.getValue());
						}

						if(lp.getKey().numOfTcOperations_recusively>5)
						{
							//System.out.println("*time-consuming operation > 5");
							setTc5Funcs.add(cgNodeInfo);
							//check(cgNodeInfo,loopingLock,lp.getKey(),lp.getValue());
						}
						if(lp.getKey().numOfTcOperations_recusively>0
								&& lp.getValue().getCircleNumInloop()>0)
						{
							//System.out.println("*time-consuming operation & circle");
							setTcCFuncs.add(cgNodeInfo);							
							//check(cgNodeInfo,loopingLock,lp.getKey(),lp.getValue());
						}
					}
				}//end looping lock for
			}//end if
		}//end cgNodes for
		nloopLock = setLockingLoops.size();
		nloopTcLock = setTCLockingLoops.size();	
		nlockTcLoop = setTcLoopingLock.size();
		nfuncTcLoopingLock = setTcLoopingLockFuncs.size();
		
		//TODO
		/* not sutable for hd3990 -> need update, useful!!!
		boolean tflag;		
		//remove the redundant entries which exist in other call path -> merge call path and keep top lock
		for(LoopingLockInfo lli: setTcLoopingLock) {
			tflag= false;
			for(LoopingLockInfo inner_lli: setTcLoopingLock) {
				for(Pair<LoopInfo,PathInfo> lp: inner_lli.getLoopPaths())	{
					if(lp.getValue().containNodeButFirst(lli.getCGNode())) {
						tflag= true;
					}
				}					
			}
			if(!tflag)
				setLoopingLockMerge.add(lli);
		}
				
		for(LoopingLockInfo lli: setLoopingLockMerge) {
			//System.out.println(lli.getLoopPaths());
			setTcLoopingLockFuncsMerge.add(cgNodeList.forceGet(lli.getCGNode()));
		}
		
		for(CGNodeInfo a : setTcLoopingLockFuncsMerge) {
			for(String bugid: bugList.keySet()) {
				if(check_each(a,bugid)) {
					resultList.get(bugid).add(a);
				}
			}
		}
		*/

		for(String bugid: resultList.keySet()) {
			System.out.println("#" + bugid+" = " + resultList.get(bugid).size());
		}
		

		System.out.println("");
		System.out.println("ZC - INFO .........final result........");
		System.out.println("");
		
		System.out.println("# func = ," +  nfunc); 
		System.out.println("# critical func = ," +   nfuncLock); 
		System.out.println("# loop func = ," +   nfuncLoop); 
		System.out.println("# loop in lock func = ," +   nfuncLoopingLock); 
		System.out.println("# loop in lock func (tcOp) = ," +   nfuncTcLoopingLock); 
		System.out.println("# loop in lock func top (tcOp) = ," +   setTcLoopingLockFuncsMerge.size()); 
		System.out.println("# loop in lock func (tcOp>5) = ," +   setTc5Funcs.size()); 
		System.out.println("# loop in lock func (tcOp circle) = ," +   setTcCFuncs.size()); 
		System.out.println("");
		
		System.out.println("# critical section = ," +   nlock); 
		System.out.println("# lock group = ," +   nlockgroup); 
		System.out.println("# lock (inner loop) = ," +   nlockLoop); 
		System.out.println("# lock (tcOp in loop) = ," +   nlockTcLoop); 
		System.out.println("# lock top (tcOp in loop) = ," +   setLoopingLockMerge.size()); 
		System.out.println("");
		
		System.out.println("# loop = ," +   nloop); 
		System.out.println("# loop in lock = ," +   nloopLock); 
		System.out.println("# loop (tcOp) = ," +   nloopTc); 
		System.out.println("# loop in lock (tcOp) = ," +   nloopTcLock); 		
		

		System.out.println("\n\n\n# loop to test bound = " +   this.loops2test.size()); 		
	}
	
	public boolean check(CGNodeInfo a,LoopingLockInfo b,LoopInfo c,PathInfo d) {
		for(String bugid: bugList.keySet()) {
			if(check_each(a,bugid)) {
				//if(c.toString().indexOf("InetAddress, getByName") >= 0) {
				if(check_each(a,"hb3483-4")) {
				//System.out.println(bugid);
				//System.out.println(a.getCGNode().getMethod().toString()); 
				//System.out.println(d);
				//System.out.println(c);
				}
				resultList.get(bugid).add(a);
				bug_loops.get(bugid).add(c); //for analysis loop bound - Class BoundedLoopAnalyzer.java
			}
		}
		return true;
	}

	
	public boolean check_each(CGNodeInfo a, String bugID) {
		return a.getCGNode().getMethod().toString().indexOf(bugList.get(bugID).toString()) >= 0;		
	}
    
	//int tCount = 0;
	HashMap<String, String> bugList = new HashMap<String, String>(){
		{
			put("mr4576", "TrackerDistributedCacheManager, getLocalCache");
			put("mr4813", "JobImpl, handle");
			put("hb3483-1", "MemStoreFlusher, reclaimMemStoreMemory");
			put("hb3483-2", "MemStoreFlusher, flushSomeRegions");
			put("hb3483-3", "MemStoreFlusher, flushRegion");
			put("hb3483-4", "HRegion, flushcache");	
			put("hb3483-5", "Store, internalFlushCache");		
			put("hd2379", "FSVolumeSet, getBlockInfo");
			put("hd5153", "BlockManager, processReport");
			put("hd3990", "DatanodeManager, fetchDatanodes");
			put("hd4186-1", "Monitor, run");			
			put("hd4186-2", "FSNamesystem, logReassignLease");
			put("hd4186-3", "FSEditlog, logSync");
		}
	};
	
	public HashMap<String,Collection<LoopInfo>> bug_loops = new HashMap<String,Collection<LoopInfo>>(){
		//for analysis loop bound - Class BoundedLoopAnalyzer.java
		{
			put("mr4576", new HashSet<LoopInfo>());
			put("mr4813", new HashSet<LoopInfo>());
			put("hb3483-1", new HashSet<LoopInfo>());
			put("hb3483-2", new HashSet<LoopInfo>());
			put("hb3483-3", new HashSet<LoopInfo>());
			put("hb3483-4", new HashSet<LoopInfo>());	
			put("hb3483-5", new HashSet<LoopInfo>());		
			put("hd2379", new HashSet<LoopInfo>());
			put("hd5153", new HashSet<LoopInfo>());
			put("hd3990", new HashSet<LoopInfo>());
			put("hd4186-1", new HashSet<LoopInfo>());
			put("hd4186-2", new HashSet<LoopInfo>());
			put("hd4186-3", new HashSet<LoopInfo>());
		}
	};
	
	HashMap<String, Set<CGNodeInfo>> resultList = new HashMap<String, Set<CGNodeInfo>>(){
		{
			put("mr4576", new HashSet<CGNodeInfo>());
			put("mr4813", new HashSet<CGNodeInfo>());
			put("hb3483-1", new HashSet<CGNodeInfo>());
			put("hb3483-2", new HashSet<CGNodeInfo>());
			put("hb3483-3", new HashSet<CGNodeInfo>());
			put("hb3483-4", new HashSet<CGNodeInfo>());	
			put("hb3483-5", new HashSet<CGNodeInfo>());		
			put("hd2379", new HashSet<CGNodeInfo>());
			put("hd5153", new HashSet<CGNodeInfo>());
			put("hd3990", new HashSet<CGNodeInfo>());
			put("hd4186-1", new HashSet<CGNodeInfo>());
			put("hd4186-2", new HashSet<CGNodeInfo>());
			put("hd4186-3", new HashSet<CGNodeInfo>());
		}
	};
	public boolean pruning(CGNodeInfo cgNodeInfo, String jarPath) {
		if(jarPath.indexOf("mr")>=0) {
			return  cgNodeInfo.getCGNode().getMethod().toString().indexOf("mapred") >= 0
					|| cgNodeInfo.getCGNode().getMethod().toString().indexOf("filecache") >= 0;
		}else if(jarPath.indexOf("hd")>=0) {
			return  cgNodeInfo.getCGNode().getMethod().toString().indexOf("hdfs") >= 0;
		}else if(jarPath.indexOf("hb")>=0) {
			return  cgNodeInfo.getCGNode().getMethod().toString().indexOf("hbase") >= 0;
		}else
			return true;
	}
}
