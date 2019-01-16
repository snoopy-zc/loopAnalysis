package sa.tcop;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.BitSet;
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
	
	
	/*
	int nHeartbeatLocks = 0;
	int nHeartbeatLockGroups = 0;
	int nSuspectedHeavyLocks = 0; 
	*/
	
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
	}
	
	// Please call doWork() manually
	public void doWork() {
		System.out.println("\nJX - INFO - LockingLoopAnalyzer: doWork...");
		findLoopsForAllLocks();
		statistic();
		printResultStatus();
		analysisPath();
	}
	
	
	public int getNLoopingLocks() {
		return this.nLoopingLocks;
	}
	
	public int getNLoopingLockingCGNodes() {
		return this.nLoopingLockingCGNodes;
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
		

		int begin_size = loopingLock.getLoopPaths().size();
		List<Pair<CGNode,PathEntry>> circle_target_Src = new ArrayList<Pair<CGNode,PathEntry>>();
	
		//add loops in the first level
		for(LoopInfo loop:loopingLock.getLoops()) {
			SSAInstruction first_ssa = instructions[cfg.getBasicBlock(loop.getBeginBasicBlockNumber()).getFirstInstructionIndex()];
			PathInfo path = new PathInfo();
			PathEntry pe = new PathEntry(cgNodeInfo,first_ssa);
			pe.delLoop(loop);
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
				SSAInstruction first_ssa = instructions[cfg.getBasicBlock(loop.getBeginBasicBlockNumber()).getFirstInstructionIndex()];
				PathInfo path = new PathInfo(callpath);
				PathEntry pe = new PathEntry(cgNodeInfo,first_ssa);
				pe.delLoop(loop);
				path.addPathEntry(pe);
				path.setFunction(cgNode);
				loopingLock.getLoopPaths().add(new Pair<>(loop,path));	
			}
		//end
		if(false&&callpath.containNode("copy") && callpath.containNode("getLocalCache") ) {
			System.err.println(callpath);
			System.err.println(cgNode.getMethod().toString());
			System.err.println(instructions);			
		}
			
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
		
		// TODO zc - this can be change !!! 
	}
	
	
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
    	
    	System.out.println("#TCLockingLoops = " + this.nTCLockingLoops + "(loop group "+ this.setTCLockingLoops.size() +")");
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
						if(lp.getKey().numOfTcOperations_recusively>0 // set specific node name
								&&lp.getValue().containNode("HRegion"))
						{
							System.out.println(lp.getValue() );
						}
					}
				}//end looping lock for
			}//end if
		}//end cgNodes for
    }

}
