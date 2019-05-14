package sa.loopsize;

import java.util.HashSet;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ssa.SSAInstruction;

public class OptCallChain {
	SSAInstruction inst;
	CGNode cgN;
	CallGraph cg;
	public OptCallChain(SSAInstruction inst,CGNode cgN, CallGraph iCG) {
		// TODO Auto-generated constructor stub
		this.inst = inst;
		this.cgN = cgN;
		this.cg = iCG;
	}
	
	public static boolean isValidSlicing(HashSet<CGNode> originList, CGNode target){
		//return true;
		System.out.println("?????????????????????????????????????");
		for(CGNode tmp : originList){
			System.out.println(tmp.getMethod().getName().toString());
		}
		System.out.println(originList.size());
		//assert 0 == 1;
		if(originList.size() == 1)
			return true;
		if(originList.isEmpty())
			return true;
		else{

			if(originList.contains(target))
				return true;
		}
		return false;
	}
}
