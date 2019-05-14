package sa.loopsize;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ssa.SSAConditionalBranchInstruction;
import com.ibm.wala.ssa.SSAInstruction;

public class IndexVar {
	CallGraph cg;
	CGNode cgNode;
	SSAInstruction ssa;
	public IndexVar(CallGraph cgIn, CGNode cgNIn, SSAInstruction ssa) {
		// TODO Auto-generated constructor stub
		this.cg = cgIn;
		this.cgNode = cgNIn;
		this.ssa = ssa;
	}

	public int isIndexVar(){
		if(this.ssa instanceof SSAConditionalBranchInstruction){
			for(int i = 0; i < this.ssa.getNumberOfUses(); i++){
				if()
			}
		}else
			return -1;
	}
}
