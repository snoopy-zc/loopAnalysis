package sa.loopsize;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSACFG;
import com.ibm.wala.ssa.SSAConditionalBranchInstruction;
import com.ibm.wala.ssa.SSAInstruction;

public class LoopCond {
	
	public static boolean isBranchCond(SSAInstruction ssa){
		if(ssa instanceof SSAConditionalBranchInstruction){
			return true;
		}
		else
			return false;
	}
	
	// bb should be a loop header 
	public static SSAInstruction containBranch(ISSABasicBlock bb){
		SSAInstruction ssa_tmp = null;
		int num = 0;
		for(SSAInstruction tmp : bb){
			if(isBranchCond(tmp)){
				num++;
				ssa_tmp = tmp;
			}
		}
		if(ssa_tmp != null)
			assert num == 1;
		return ssa_tmp;
	}

	// find a collection-related branch if exits 
	public static SSAInstruction collectionBranch(ISSABasicBlock bb,CGNode currentCGN){
		ISSABasicBlock relocateBB = SSAUtil.ifHasNextBB(bb,currentCGN.getIR().getControlFlowGraph());
		SSAInstruction target = null;
		int num = 0;
		if(relocateBB != null){
			for(SSAInstruction ssa_tmp : bb){
				if(isBranchCond(ssa_tmp)){
					target = ssa_tmp;
					num++;
				}
			}
		}
		assert (num <= 1);
		if(target != null && num == 1){
			if(branchOp(target).equals("eq"))
				return target;
		}
		return null;
	}
	
	//for a branch instruction, return its operator 
	public static String branchOp(SSAInstruction ssa){
		assert (ssa instanceof SSAConditionalBranchInstruction);
		return ((SSAConditionalBranchInstruction) ssa).getOperator().toString();
	}
	
	//the branch may be in the successor of loop header  
	public static SSAInstruction getInDirectBranch(ISSABasicBlock bb, CGNode cgN){
		SSAInstruction ssa = null;
		SSACFG cfg = cgN.getIR().getControlFlowGraph();
		ISSABasicBlock ibb = null;
		System.out.println(bb.toString());
		System.out.println(cgN.getMethod().getName().toString());
		System.out.println(cfg.getSuccNodeCount(bb));
		if(cfg.getSuccNodeCount(bb) == 1){
			for(ISSABasicBlock tmp : cfg.getNormalSuccessors(bb)){
				ibb = tmp;
			}
			if(ibb != null){
				return containBranch(ibb);
			}
		}else if(cfg.getSuccNodeCount(bb) == 2){
			for(ISSABasicBlock tmp : cfg.getNormalSuccessors(bb)){
				if(tmp.getNumber() == cfg.exit().getNumber())
					continue;
				else
					return containBranch(tmp);
			}
		}
		return ssa;
	}
	
	//while(true) case
	public static boolean isWhileTrueCase(ISSABasicBlock bb, CGNode cgN){
		if(containBranch(bb) == null && getInDirectBranch(bb,cgN) == null && collectionBranch(bb,cgN) == null){
			return true;
		}
		return false;
	}
}
