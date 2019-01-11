package sa.tcop;

import java.util.ArrayList;
import java.util.List;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;

import sa.lockloop.CGNodeInfo;
import sa.loop.LoopInfo;
import sa.wala.IRUtil;

public class TcOpPathInfo {
	public SSAInstruction tcOp_ssa = null;   //this is the core, the tc operation
	public CGNode function = null;	//the lowest level 
	public int line_number = -1;
	public List<PathEntry> callpath = null; //must not null if used
	
	public TcOpPathInfo(CGNodeInfo cgN, SSAInstruction ssa) {
		this.function = cgN.getCGNode();
		this.tcOp_ssa = ssa;
		this.line_number = IRUtil.getSourceLineNumberFromSSA(cgN.getCGNode(), ssa);
		this.callpath = new ArrayList<PathEntry>();	
		//nothing
		
	}
	
	public TcOpPathInfo(TcOpPathInfo e) {
		this.tcOp_ssa = e.tcOp_ssa;
		this.function = e.function;
		this.line_number = e.line_number;
		this.callpath = new ArrayList<PathEntry>();
		for(PathEntry entry: e.callpath) {
			this.callpath.add(new PathEntry(entry));
		}
	}
	
	public int getNestedLoopNum() {
		if (callpath==null)
			return 0;
		int sum = 0;
		for (PathEntry entry : callpath) {
			sum += entry.getLoopNum();
		}
		return sum;
	}

	@Override
	public String toString() {
		String result = "TcOpPathInfo:" + line_number;
		for(int i = callpath.size()-1; i>=0; i++) {
			CGNode cgNode = callpath.get(i).function;
			result = result + "-" + cgNode.getMethod().getSignature().substring(0, cgNode.getMethod().getSignature().indexOf('('));
		}
		result = result + "@" + ((SSAInvokeInstruction)tcOp_ssa).getDeclaredTarget(); 
		return result;
	}
}


class PathEntry{
	public CGNode function = null;
	public List<LoopInfo> loops = null;
	//TODO outer loop in the front?? it make sense
	
	public PathEntry(CGNode n) {
		function = n;
	}
	
	public PathEntry(PathEntry e) {
		this.function = e.function;
		if(e.loops != null) {
			for(LoopInfo loop : e.loops) {
				this.addLoop(loop);
			}
		}
	}
	
	public PathEntry(CGNodeInfo cgN, SSAInstruction ssa) {
		this.function = cgN.getCGNode();
		if(cgN.hasLoops()) {
			for(LoopInfo loop: cgN.getLoops()) {
				//just for test
				if(loop.containsSSA(ssa)!=loop.isContain(ssa))
					System.err.println("ZC - error !!!! loop.containsSSA(ssa)!=loop.isContain(ssa)");
				if(loop.containsSSA(ssa))
					this.addLoop(loop);
			}
		}
	}
	
	public void addLoop(LoopInfo loop) {
		if(loops == null)
			loops = new ArrayList<LoopInfo>();
		loops.add(loop);
	}
	
	public int getLoopNum() {
		return loops==null? 0:loops.size();
	}
	
	public String getFunctionName() {
		return function.getIR().getMethod().toString();
	}
}
