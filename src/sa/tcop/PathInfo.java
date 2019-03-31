package sa.tcop;

import java.util.ArrayList;
import java.util.List;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;

import sa.lockloop.CGNodeInfo;
import sa.loop.LoopInfo;
import sa.wala.IRUtil;

public class PathInfo {
	public SSAInstruction ssa = null;   //this is the core, the tc operation
	public CGNode function = null;	//the lowest level 
	public int line_number = -1;
	public List<PathEntry> callpath = null; //must not null if used

	
	public PathInfo() {
		this.callpath = new ArrayList<PathEntry>();	
		//nothing		
	}
	
	public PathInfo(PathInfo e) {
		this.ssa = e.ssa;
		this.function = e.function;
		this.line_number = e.line_number;
		this.callpath = new ArrayList<PathEntry>();
		for(PathEntry entry: e.callpath) {
			this.callpath.add(new PathEntry(entry));
		}
	}
	
	public PathInfo(CGNode cgN, SSAInstruction ssa) {
		this.setSSA(cgN,ssa);
		this.callpath = new ArrayList<PathEntry>();	
		//nothing		
	}
	
	public PathInfo(CGNodeInfo cgN, SSAInstruction ssa) {
		this.setSSA(cgN.getCGNode(),ssa);
		this.callpath = new ArrayList<PathEntry>();	
		//nothing
	}
	
	public void setSSA(CGNode cgN, SSAInstruction ssa) {
		this.function = cgN;
		this.ssa = ssa;
		this.line_number = IRUtil.getSourceLineNumberFromSSA(cgN, ssa);
	}
	
	public void setFunction(CGNode cgN) {
		this.function = cgN;
	}
	
	public int getNestedLoopDepth() {
		int sum = 0;
		for (PathEntry entry : callpath) {
			sum += entry.getLoopNum();
		}
		return sum;
	}
	
	public int getCircleNum() {
		int sum = 0;
		for (PathEntry entry : callpath) {
			sum += entry.getCircleNum();
		}
		return sum;
	}

	public int getCircleNumInloop() {//TODO incomplete, only count circle call in current-level loop
		int sum = 0;
		for (PathEntry entry : callpath) {
			if(entry.circleCalls != null)
				for(PathEntry e:entry.circleCalls)
					if(e.getLoopNum()>0)
						sum ++;
		}
		return sum;
	}
	
	public boolean containNode(String str) {
		//e.g. org/apache/hadoop/hdfs/server/namenode/INodeDirectory, spaceConsumedInTree(...
		for (PathEntry entry : callpath) {
			if(entry.getFunctionName().indexOf(str)>=0)
				return true;
		}		
		return false;
	}
	
	public boolean containNode(CGNode cgn) {
		//e.g. org/apache/hadoop/hdfs/server/namenode/INodeDirectory, spaceConsumedInTree(...
		for (PathEntry entry : callpath) {
			if(entry.getCGNode() == cgn)
				return true;
		}
		return false;
	}
	public boolean containNodeButFirst(CGNode cgn) {
		//e.g. org/apache/hadoop/hdfs/server/namenode/INodeDirectory, spaceConsumedInTree(...
		for(int i=1; i<this.callpath.size();i++) {
		//for(int i=this.callpath.size()-2; i>=0;i--) {		//wrong traverse direction		
			if(this.callpath.get(i).function.equals(cgn))
				return true;
		}
		return false;
	}
	
	public PathEntry getPathNode(CGNode cgn) {//-1 means no! 0 means self call! n>0 stand for the n.th node after this call this 
		for(int i=0; i<this.callpath.size();i++)
			if(this.callpath.get(i).function.equals(cgn))
				return this.callpath.get(i);
		return null;
	}
	
	public void addPathEntry(PathEntry e) {
		this.callpath.add(e);
	}
	
	
	@Override
	public String toString() {
		if(callpath.size()<1)
			return null;
		String result = "CallPathInfo:" 
			+ "(CinL-" + this.getCircleNumInloop() + ")"
			+ "L" + this.getNestedLoopDepth()
			+ "C" + this.getCircleNum();
		boolean reverse = false;
		if(reverse) {
			for(int i = callpath.size()-1; i>=0; i--) {
				PathEntry pe = callpath.get(i);
				CGNode cgNode = pe.function;
				result = result + " - " + cgNode.getMethod().getSignature().substring(0, cgNode.getMethod().getSignature().indexOf('(')) 
						+"#L("+ pe.getLoopNum() + ")#C(" + pe.getCircleNum() + ")";
			}
		} else {
			for(int i = 0; i < callpath.size(); i++) {
				PathEntry pe = callpath.get(i);
				CGNode cgNode = pe.function;
				result = result + "-" + cgNode.getMethod().getSignature().substring(0, cgNode.getMethod().getSignature().indexOf('(')) 
						+"#L("+ pe.getLoopNum() + ")#C(" + pe.getCircleNum() + ")";
			}
		}
		if(ssa!=null)
			result = result + "@" + ((SSAInvokeInstruction)ssa).getDeclaredTarget(); 
		return result;
	}	
}


class PathEntry{
	public CGNode function = null;
	public SSAInstruction ssa = null;
	public List<LoopInfo> loops = null;
	//TODO outer loop in the front?? it make sense
	public List<PathEntry> circleCalls = null; //who call me
	//-1 stand for no circle! 0 strand for self call! n>0 stand for the n.th node after this call this 
	
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
		this.ssa = ssa;
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
	
	public void delLoop(LoopInfo loop) {
		if(loops != null)
			loops.remove(loop);
	}
	
	public int getLoopNum() {
		return loops==null? 0:loops.size();
	}
	
	public int getCircleNum() {
		return circleCalls==null? 0:circleCalls.size();
	}
	
	//-1 stand for no circle! 0 strand for self call! n>0 stand for the n.th node after this call this 
	public void addCircle(PathEntry e) {
		if(circleCalls == null)
			circleCalls = new ArrayList<PathEntry>();
		circleCalls.add(e);
	}
	
	public String getFunctionName() {
		return function.getMethod().toString();
	}
	public CGNode getCGNode() {
		return function;
	}	
}
