package sa.loopsize;

import java.util.HashSet;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ssa.SSAInstruction;

public class LoopInvariant {
	HashSet<SSAInstruction> ssaInLoop;
	public LoopInvariant(HashSet<SSAInstruction> instsInLoop) {
		// TODO Auto-generated constructor stub
		this.ssaInLoop = instsInLoop;
	}
	
	public HashSet<SSAInstruction> getAllUpdate(int a, CGNode cgn){
		HashSet<SSAInstruction>
	}

}
