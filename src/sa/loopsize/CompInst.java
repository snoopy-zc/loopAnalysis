package sa.loopsize;

import java.util.HashSet;
import java.util.Set;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ssa.SSAInstruction;

public class CompInst {
	int valNum; // in condition branch, what's the int value for this variable 
	BackwardSlicing bHelper;
	CGNode cgNode;
	SSAInstruction ssaInst; //get immediate ssa 
	HashSet<SSAInstruction> analyzedSSA; 
	
	public CompInst(int x, CGNode cgN){
		this.valNum = x;
		this.cgNode = cgN;
		this.analyzedSSA = new HashSet<SSAInstruction>();
		System.out.println("xxxxxxxx");
	}
	
	
}
