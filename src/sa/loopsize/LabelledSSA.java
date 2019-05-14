package sa.loopsize;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ssa.SSAInstruction;

public class LabelledSSA {
	SSAInstruction ssaInst;
	CGNode cgNode;
	int level = -1; // for caller finding...
	int para_loc = -1;
	CGNode parent;
	public LabelledSSA(SSAInstruction inst, CGNode cgN) {
		// TODO Auto-generated constructor stub
		this.ssaInst = inst;
		this.cgNode = cgN;
	}
	
	public LabelledSSA(SSAInstruction inst, CGNode cgN, int level) {
		// TODO Auto-generated constructor stub
		this.ssaInst = inst;
		this.cgNode = cgN;
		this.level = level;
	}
	
	public LabelledSSA(SSAInstruction inst, CGNode cgN, int level, int para_loc) {
		// TODO Auto-generated constructor stub
		this.ssaInst = inst;
		this.cgNode = cgN;
		this.level = level;
		this.para_loc = para_loc;
	}
	
	
	public void setParent(CGNode cgn){
		this.parent = cgn;
	}
	
	public CGNode getParent(){
		return this.parent;
	}
	
	public int getPara(){
		return this.para_loc;
	}
	
	public int getLevel(){
		return this.level;
	}
	
	public void setLevel(int level){
		this.level = level;
	}
	
	public SSAInstruction getSSA(){
		return this.ssaInst;
	}
	
	public void setSSA(SSAInstruction ssa){
		this.ssaInst = ssa;
	}
	
	public CGNode getCGNode(){
		return this.cgNode;
	}
	
	public void setCGNode(CGNode cgN){
		this.cgNode = cgN;
	}
}
