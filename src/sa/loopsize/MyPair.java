package sa.loopsize;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ssa.SSAInstruction;

public class MyPair {
	private SSAInstruction l;
	private int r;
	public MyPair(SSAInstruction l, int r){
		this.l = l;
		this.r = r;
	}
	
	private String className;
	private CGNode cgn;
	private SSAInstruction ssa;
	
	public MyPair(String s, SSAInstruction ssa, CGNode cgn){
		this.className = s;
		this.cgn = cgn;
		this.ssa = ssa;
	}
	
	public String getClassName(){
		return this.className;
	}
	public CGNode getCGN(){
		return this.cgn;
	}
	
	public SSAInstruction getSSA(){
		return this.ssa;
	}
	public SSAInstruction getL(){
		return this.l;
	}
	public void setL(SSAInstruction l){
		this.l = l;
	}
	public int getR(){
		return this.r;
	}
	public void setR(int r){
		this.r = r;
	}
}
