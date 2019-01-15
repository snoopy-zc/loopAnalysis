package sa.tcop;

import java.nio.file.Path;
import java.util.BitSet;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.types.ClassLoaderReference;

import sa.lock.LockAnalyzer;
import sa.lockloop.CGNodeInfo;
import sa.lockloop.CGNodeList;
import sa.loop.TCOpUtil;
import sa.wala.WalaAnalyzer;

public class TcOpAnalyzer {

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
	
	
	
	
	public TcOpAnalyzer(WalaAnalyzer walaAnalyzer, LockAnalyzer lockAnalyzer, CGNodeList cgNodeList) {
		this.walaAnalyzer = walaAnalyzer;
		this.cg = this.walaAnalyzer.getCallGraph();
		this.outputDir = this.walaAnalyzer.getTargetDirPath();
		this.lockAnalyzer = lockAnalyzer;
		this.cgNodeList = cgNodeList;
		//others
		this.iolooputil = new TCOpUtil( this.walaAnalyzer.getTargetDirPath() );
	}
	
	// Please call doWork() manually
	public void doWork() {
		System.out.println("\nJX - INFO - TCOpAnalyzer: doWork...");
		
		findTCOperations();
		iolooputil.printTcOperationTypes();                //for test	      
		printResultStatus();
	}
	
	/**************************************************************************
	 * JX - find time-consuming operations and store in cgNode
	 **************************************************************************/
	
	public void findTCOperations() {
		System.out.println("ZC - INFO - findTCOperations");
		// Initialize Time-consuming operation information by DFS for all looping functions
		BitSet traversednodes = new BitSet();
		traversednodes.clear();
		
		System.err.println(this.cgNodeList);
		for (CGNodeInfo cgNodeInfo: this.lockAnalyzer.getLockCGNodes()) {
		//for (CGNodeInfo cgNodeInfo: this.cgNodeList.values()) { //the cgNodeList will change and throw exception
			dfsToGetTCOperations(cgNodeInfo.getCGNode(), 0, traversednodes);
		}
		System.err.println(this.cgNodeList);		
		for (CGNodeInfo cgNodeInfo: this.cgNodeList.values()) {
			if(cgNodeInfo.getTcOps().size()>0) {
				this.nTCNodes++ ;
			}
		}
	}
	  
	  
	public int dfsToGetTCOperations(CGNode cgNode, int depth, BitSet traversednodes) {//zc: travel all code and mark the loop info
    
		// for test - the depth can reach 58
		/*
		if (depth > 50) {
			System.err.println("JX - WARN - depth > " + depth);
		}
		 */

		if ( !walaAnalyzer.isInPackageScope(cgNode) ) { 
			return 0;
		}
		
		if ( !cgNode.getMethod().getDeclaringClass().getClassLoader().getReference().equals(ClassLoaderReference.Application) 
				|| cgNode.getMethod().isNative()) { // IMPO - native - must be
			return 0;
		}

		int id = cgNode.getGraphNodeId();
		CGNodeInfo cgNodeInfo = cgNodeList.forceGet(id);
    
		if ( traversednodes.get( id ))            // if has already been traversed, then return
			return cgNodeInfo.tcOps.size(); //maybe 0(unfinished) or realValue(finished)
       
		traversednodes.set( id );                  // if hasn't been traversed
				
		IR ir = cgNode.getIR();  //if (ir == null) return;
		SSAInstruction[] instructions = ir.getInstructions();
 
		for (int i = 0; i < instructions.length; i++) {
			SSAInstruction ssa = instructions[i];
			if (ssa == null) continue;

			if ( iolooputil.isTimeConsumingSSA(ssa) ) {
				PathInfo tmp = new PathInfo(cgNodeInfo,ssa);			
				tmp.callpath.add(new PathEntry(cgNodeInfo,ssa));
				cgNodeInfo.tcOps.add(tmp);
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
					if (sub_cgnode.equals(cgNode)) {//self call mark
						//System.err.println(ssa==null);//ssa must be not null
						cgNodeInfo.markSelfCall(ssa);
						continue;
					}
					int result = dfsToGetTCOperations(sub_cgnode, depth+1, traversednodes);
					//if (result > 0 && WalaAnalyzer.isApplicationMethod(sub_cgnode)) {
					if (result > 0) {
						CGNodeInfo sub_cgnodeInfo = cgNodeList.forceGet(sub_cgnode.getGraphNodeId());						
						for(PathInfo tcPath: sub_cgnodeInfo.getTcOps()) {
							PathInfo curTcPath = new PathInfo(tcPath);
							curTcPath.callpath.add(new PathEntry(cgNodeInfo,ssa));						
							cgNodeInfo.tcOps.add(curTcPath);
						}
					}
				}
			} else {
				// TODO - if need be
			}
		}//for	 
		return cgNodeInfo.tcOps.size(); //maybe 0(unfinished) or realValue(finished)    
	} 	
	 
    public void printResultStatus() {
    	System.out.println("ZC - INFO - TcOpAnalyzer: printResultStatus");
    	System.out.println("#TCNodes = " + this.nTCNodes + " out of total " + this.cgNodeList.size() + " CgNodes"); 
    }
    
    
}
