package sa.loopsize;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ssa.SSAInstruction;

public class MyTuple {
	SSAInstruction ssa;
	CGNode cgNode;
	boolean result;
	int paraLoc = -1;
	
	public MyTuple(SSAInstruction ssa,CGNode cgNode,boolean result, int paraLoc) {
		// TODO Auto-generated constructor stub
		this.ssa = ssa;
		this.cgNode = cgNode;
		this.result = result;
	}

	public int getParaLoc(){
		return this.paraLoc;
	}
	
	public void setParaLoc(int para){
		this.paraLoc = para;
	}
	
	public SSAInstruction getSSA(){
		return this.ssa;
	}
	
	public void setSSA(SSAInstruction ssa){
		this.ssa = ssa;
	}
	
	public CGNode getCGN(){
		return this.cgNode;
	}
	
	public void setCGN(CGNode cgNode){
		this.cgNode = cgNode;
	}
	
	public boolean getResult(){
		return this.result;
	}
	
	public void setResult(boolean result){
		this.result = result;
	}
}
