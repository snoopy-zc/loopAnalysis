package sa.tcop;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Iterator;
import java.util.List;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.types.ClassLoaderReference;

import javafx.util.Pair;
import sa.lock.LockAnalyzer;
import sa.lock.LockInfo;
import sa.lock.LoopingLockInfo;
import sa.lockloop.CGNodeInfo;
import sa.lockloop.CGNodeList;
import sa.loop.LoopInfo;
import sa.loop.TCOpUtil;
import sa.wala.WalaAnalyzer;

public class LockLoopTcOpAnalyzer {

	// wala
	WalaAnalyzer walaAnalyzer;
	CallGraph cg;
	Path outputDir;
	// database
	CGNodeList cgNodeList = null;	
	// loop
	LockAnalyzer lockAnalyzer;
	TCOpUtil iolooputil;
	
	// results
	int nTCNodes = 0;
	int nTCLocks = 0;
	
	List<TcOpPathInfo> results;
	
	
	
	
	public LockLoopTcOpAnalyzer(WalaAnalyzer walaAnalyzer, LockAnalyzer lockAnalyzer, CGNodeList cgNodeList) {
		this.walaAnalyzer = walaAnalyzer;
		this.cg = this.walaAnalyzer.getCallGraph();
		this.outputDir = this.walaAnalyzer.getTargetDirPath();
		this.lockAnalyzer = lockAnalyzer;
		this.cgNodeList = cgNodeList;
		this.results = new ArrayList<TcOpPathInfo>();
		//others
		this.iolooputil = new TCOpUtil( this.walaAnalyzer.getTargetDirPath() );
	}
	
	// Please call doWork() manually
	// tcloopAnalyzer ( loop -> tc Op ) has been done
	// loopingLockAnalyzer ( lock -> loop ) is incomplete, only for recursive max depth and first level loopInfo, but no recursive loopInfo 
	public void doWork() {
		System.out.println("\nZC - INFO - LockLoopTcOpAnalyzer: doWork...");		
		findTCOperations();    
		printResultStatus();
	}
	
	/**************************************************************************
	 * JX - find each loop in lock field, and filter time-consuming loop
	 **************************************************************************/
	
	public void findTCOperations() {
		System.out.println("ZC - INFO - findLockLoopTCOperations");
		// Initialize Time-consuming operation information by DFS for all looping functions
		BitSet traversednodes = new BitSet();
		traversednodes.clear();
		
		System.err.println(this.cgNodeList);
		for (CGNodeInfo cgNodeInfo: this.lockAnalyzer.getLockCGNodes()) {
		//for (CGNodeInfo cgNodeInfo: this.cgNodeList.values()) { //the cgNodeList will change and throw exception
			CGNode cgNode = cgNodeInfo.getCGNode();
			IR ir = cgNode.getIR();  //if (ir == null) return;
			SSAInstruction[] instructions = ir.getInstructions();
			
			if (cgNodeInfo.hasLoopingLocks) {
				for(LoopingLockInfo lpLock : cgNodeInfo.looping_locks.values()) {
					LockInfo lock = lpLock.getLock();

					int begin_size = results.size();
					List<Pair<CGNode,PathEntry>> circle_target_Src = new ArrayList<Pair<CGNode,PathEntry>>();			
					
					for (Iterator<Integer> it = lock.bbs.iterator(); it.hasNext(); ) {
						int bbnum = it.next();
						int first_index = ir.getControlFlowGraph().getBasicBlock(bbnum).getFirstInstructionIndex();
						int last_index = ir.getControlFlowGraph().getBasicBlock(bbnum).getLastInstructionIndex();
						for (int index = first_index; index <= last_index; index++) {
							SSAInstruction ssa = instructions[index];
							if (ssa == null)
								continue;
							if ( iolooputil.isTimeConsumingSSA(ssa) ) {
								TcOpPathInfo curPathInfo = new TcOpPathInfo();	
								curPathInfo.callpath.add(new PathEntry(cgNodeInfo,ssa));
								curPathInfo.setTcOp(cgNode, ssa);
								if(cgNodeInfo.hasLoops())//remove outer loop of lock
									for(LoopInfo loop:cgNodeInfo.getLoops())
										if(!lock.bbs.containsAll(loop.getBasicBlockNumbers()))
											curPathInfo.getPathNode(cgNode).loops.remove(loop);
								if(curPathInfo.getNestedLoopNum()>0)//add path with loop to result sets
									results.add(curPathInfo);
								continue; //for test // TODO
							}							
							// filter the rest I/Os
							if ( iolooputil.isJavaIOSSA(ssa) )
								continue;
							
							// if meeting a normal call(NOT RPC and I/O), Go into the call targets
							if (ssa instanceof SSAInvokeInstruction) {  //SSAAbstractInvokeInstruction
								SSAInvokeInstruction invokessa = (SSAInvokeInstruction) ssa;   
								// get all possible targets
								java.util.Set<CGNode> set = cg.getPossibleTargets(cgNode, invokessa.getCallSite());
								
								// traverse all possible targets
								for (CGNode sub_cgnode: set) {
									//TcOpPathInfo curPathInfo = new TcOpPathInfo(pPathInfo);	
									if (sub_cgnode.equals(cgNode)) {//self call mark
										System.err.println("Self-call!! "+cgNode.getMethod().toString());
										circle_target_Src.add(new Pair<>(sub_cgnode,new PathEntry(cgNodeInfo,ssa)));
										continue;
									}
									//else do next level
									TcOpPathInfo curPathInfo = new TcOpPathInfo();	
									curPathInfo.callpath.add(new PathEntry(cgNodeInfo,ssa));					
									doRecursiveCollection(sub_cgnode, curPathInfo, results);
								}
							} else {
								// TODO - if need be
							}
						}						
					}
					//mark circle call in path node
					for(int i=begin_size;i<results.size();i++) {
						for(Pair<CGNode,PathEntry> p : circle_target_Src) {
							results.get(i).getPathNode(p.getKey()).addCircle(p.getValue());
						}
					}
					//remove outer loop of lock
					for(int i=begin_size;i<results.size();i++) {
						if(cgNodeInfo.hasLoops())
							for(LoopInfo loop:cgNodeInfo.getLoops())
								if(!lock.bbs.containsAll(loop.getBasicBlockNumbers()))
										results.get(i).getPathNode(cgNode).loops.remove(loop);
					}
					
					if(begin_size < results.size())
						nTCLocks++;
				}//end loopingLock for

				System.gc();//Manually GC
			}//end if
		}//for cgNodeInfo
	}
	
	public int doRecursiveCollection(CGNode cgNode, TcOpPathInfo pPathInfo, List<TcOpPathInfo> resCellection){

		// for test - the depth can reach 58
		/*
		if (depth > 50) {
			System.err.println("JX - WARN - depth > " + depth);
		}
		 */
		if ( !walaAnalyzer.isInPackageScope(cgNode) ) { 
			return 0;
		}
		
		if (pPathInfo.callpath.size()>100) {
			System.err.println("ZC - WARN - depth > " + 100);
			System.err.println(pPathInfo);
			return 0;
		}
		
		if ( !cgNode.getMethod().getDeclaringClass().getClassLoader().getReference().equals(ClassLoaderReference.Application) 
				|| cgNode.getMethod().isNative()) { // IMPO - native - must be
			return 0;
		}
		
		int current_result_size = resCellection.size();
		List<Pair<CGNode,PathEntry>> circle_target_Src = new ArrayList<Pair<CGNode,PathEntry>>();
		
		int id = cgNode.getGraphNodeId();
		CGNodeInfo cgNodeInfo = cgNodeList.forceGet(id); 
				
		IR ir = cgNode.getIR();  //if (ir == null) return;
		SSAInstruction[] instructions = ir.getInstructions();
 
		for (int i = 0; i < instructions.length; i++) {
			SSAInstruction ssa = instructions[i];
			if (ssa == null) continue;
			
			if ( iolooputil.isTimeConsumingSSA(ssa) ) {
				TcOpPathInfo curPathInfo = new TcOpPathInfo(pPathInfo);	
				curPathInfo.callpath.add(new PathEntry(cgNodeInfo,ssa));
				curPathInfo.setTcOp(cgNode, ssa);
				resCellection.add(curPathInfo);
				//continue; //for test // TODO
			}
			
			// filter the rest I/Os
			if ( iolooputil.isJavaIOSSA(ssa) )
				continue;      
			
			// if meeting a normal call(NOT RPC and I/O), Go into the call targets
			if (ssa instanceof SSAInvokeInstruction) {  //SSAAbstractInvokeInstruction
				SSAInvokeInstruction invokessa = (SSAInvokeInstruction) ssa;   
				// get all possible targets
				java.util.Set<CGNode> set = cg.getPossibleTargets(cgNode, invokessa.getCallSite());
				
				// traverse all possible targets
				for (CGNode sub_cgnode: set) {
					//TcOpPathInfo curPathInfo = new TcOpPathInfo(pPathInfo);	
					if (sub_cgnode.equals(cgNode)) {//self call mark
						System.err.println("Self-call!! "+cgNode.getMethod().toString());
						circle_target_Src.add(new Pair<>(sub_cgnode,new PathEntry(cgNodeInfo,ssa)));
						continue;
					}
					if (pPathInfo.getPathNode(sub_cgnode) != null) {
						circle_target_Src.add(new Pair<>(sub_cgnode,new PathEntry(cgNodeInfo,ssa)));
						System.err.println("Circle-call!! "+cgNode.getMethod().toString());
						continue;
					}
					//else do next level
					TcOpPathInfo curPathInfo = new TcOpPathInfo(pPathInfo);	
					curPathInfo.callpath.add(new PathEntry(cgNodeInfo,ssa));					
					doRecursiveCollection(sub_cgnode, curPathInfo, resCellection);
				}
			} else {
				// TODO - if need be
			}
		}//for	 
		
		//mark circle call in path
		for(int i=current_result_size;i<resCellection.size();i++) {
			for(Pair<CGNode,PathEntry> p : circle_target_Src) {
				resCellection.get(i).getPathNode(p.getKey()).addCircle(p.getValue());
			}
		}		
		//System.gc();//Manually GC
		return 0;		
	}
	   
    public void printResultStatus() {
    	System.out.println("ZC - INFO - LockLoopTcOpAnalyzer: printResultStatus");
    	System.out.println("#TCLocks = " + this.nTCLocks + " with " + this.results.size() + " TCLoops Path"); 
    }
    
    
}
