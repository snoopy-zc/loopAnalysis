package sa.loopsize;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ssa.SSAInstruction;

public class MyTriple {
	private SSAInstruction ssa;
	private int paraLoc;
	private CGNode cgN;
	private boolean hasChild = true;
	private boolean bounded = false;
	private boolean notFoundCaller = false;
	HashSet<String> confVar = new HashSet<String>();
	HashSet<String> constantVar = new HashSet<String>();
	LabelledSSA retSource; // for a return case, record its source, if when analyzing the return, find it's related with parameter
						    // we can only analyze this point 
	boolean retSourceFlag = false;
	LinkedList<String> info = new LinkedList<String>();
	MyTriple parent;
	private boolean hasParent = false;
	private boolean isLibraryInvovled = false;
	private boolean isCABounded = false;
	public OptCallChain optChain = null;
	//HashMap<String,String> constantVar = new HashMap<String,String>();
	public MyTriple(SSAInstruction l, CGNode cgNode, int r){
		this.ssa = l;
		this.paraLoc = r;
		this.cgN = cgNode;
		this.confVar = new HashSet<String>();
		this.constantVar = new HashSet<String>();
	}
	
	public void setOptChain(SSAInstruction ssa, CGNode cgn, CallGraph cg){
		this.optChain = new OptCallChain(ssa,cgn,cg);
	}
	
	public SSAInstruction getSSA(){
		return this.ssa;
	}
	public void setSSA(SSAInstruction ssa){
		this.ssa = ssa;
	}
	
	public int getParaLoc(){
		return this.paraLoc;
	}
	public void setParaLoc(int r){
		this.paraLoc = r;
	}
	
	public CGNode getCGNode(){
		return this.cgN;
	}
	
	public void setCGNode(CGNode cgN){
		this.cgN = cgN;
	}
	
	public void setHasChild(boolean hasOrNot){
		this.hasChild = hasOrNot;
	}
	
	public boolean getHasChild(){
		return this.hasChild;
	}
	
	public void setBounded(boolean boundedOrNot){
		this.bounded = boundedOrNot;
	}
	
	public boolean getBounded(){
		return this.bounded;
	}
	
	public void addConfVar(HashSet<String> result){
		this.confVar.addAll(result);
	}
	
	public void addConstantVar(HashSet<String> result){
		this.constantVar.addAll(result);
	}
	
	public boolean hasconfRelated(){
		return this.confVar.size() == 0 ? false : true;
	}
	
	public boolean hasConstantVar(){
		return this.constantVar.size() == 0 ? false : true;
	}
	
	public void setRetCallLoc(CGNode cgN, SSAInstruction ssa){
		retSource = new LabelledSSA(ssa,cgN);	
		retSourceFlag = true;
	}
	
	public void addInfo(LinkedList<String> inInfo){
		//assert this.parent.hasParent == true;
		//this.info.addAll(this.parent.info); // add parent's info first 
		this.info.addAll(inInfo);
	}
	
	public boolean hasInfo(){
		return this.info.size() == 0 ? false : true;
	}
	
	public void setHasParent(){
		this.hasParent = true;
	}
	
	public void setParent(MyTriple x){
		this.hasParent = true;
		this.parent = x;
	}
	
	public MyTriple getParent(){
		return this.parent;
	}
	
	public void setNotFoundCaller(boolean x){
		this.notFoundCaller = x;
	}
	
	public boolean getNotFoundCaller(){
		return this.notFoundCaller;
	}
	
	public void setLibraryInvovled(){
		this.isLibraryInvovled = true;
	}
	
	public boolean getLibraryInvovled(){
		return this.isLibraryInvovled;
	}
	
	public void setCABounded(){
		this.isCABounded = true;
	}
	
	public boolean getCABounded(){
		return this.isCABounded;
	}
	
}
