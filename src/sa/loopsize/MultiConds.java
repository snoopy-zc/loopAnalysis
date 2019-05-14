package sa.loopsize;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSACFG;
import com.ibm.wala.ssa.SSAConditionalBranchInstruction;
import com.ibm.wala.ssa.SSACFG.BasicBlock;
import com.ibm.wala.ssa.SSAInstruction;

import sa.loop.LoopAnalyzer;
import sa.loop.LoopInfo;

public class MultiConds {
	CallGraph cg;
	LoopAnalyzer looper;
	HashSet<SSAInstruction> multiConds;
	CGNode cgN;
	SSAInstruction ssa;
	LoopInfo targetInfo;
	HashSet<SSAInstruction> condSSA;
	HashSet<ISSABasicBlock> bbInLoop;
	SSACFG cfg;
	public MultiConds(CallGraph cg, CGNode cgN, SSAInstruction ssa, LoopAnalyzer looper) {
		// TODO Auto-generated constructor stub
		this.cg = cg;
		this.looper = looper;
		this.cgN = cgN;
		this.ssa = ssa;
		this.condSSA = new HashSet<SSAInstruction>();
		this.bbInLoop = new HashSet<ISSABasicBlock>();
		this.cfg = this.cgN.getIR().getControlFlowGraph();
	}
	
	public void doWorks(){
		getCorrespondingLoop();
		getAllBBInLoop();
		getAllExitConds();
		System.out.println("print all exit conds for loop");
		System.out.println("xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");
		for(SSAInstruction x : this.condSSA){
			System.out.println(x.toString());
		}
		System.out.println("xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");
	}
	
	public void getCorrespondingLoop(){
		List<LoopInfo> ret = new LinkedList<LoopInfo>();
		List<LoopInfo> loops = this.looper.findLoopsForCGNode(this.cgN);
		for(LoopInfo x : loops){
			if(x.containsSSA(this.ssa)){
				ret.add(x);
			}
		}
		if(ret.size() == 1)
			//return ret.get(0);
			this.targetInfo = ret.get(0);
		else{ // nest loop
			int tmp = 1000;
			LoopInfo n = null;
			for(LoopInfo m : ret){
				if(tmp >= m.getBasicBlockNumbers().size()){
					tmp = m.getBasicBlockNumbers().size();
					n = m;
				}  
			}
			//return n;
			this.targetInfo = ret.get(0);
		}
	}
	
	public void getAllBBInLoop(){
		Set<Integer> x = this.targetInfo.getBasicBlockNumbers();
		for(Integer m : x){
			this.bbInLoop.add(this.cfg.getBasicBlock(m.intValue()));
		}
	}
	
	public void getAllExitConds(){
		for(ISSABasicBlock x: this.bbInLoop){
			SSAInstruction ssa = hasBranchInst(x);
			if(ssa != null){
				if(isExitCond(x,this.cfg,this.bbInLoop)){
					this.condSSA.add(ssa);
				}
			}
		}
	}
	
	public static SSAInstruction hasBranchInst(ISSABasicBlock bb){
		BasicBlock m = (BasicBlock)bb;
		for(SSAInstruction inst : m.getAllInstructions()){
			if(inst instanceof SSAConditionalBranchInstruction){ //one bb can only have one conditional branch
				return inst;
			}
		}
		return null;
	}
	
	public static boolean isExitCond(ISSABasicBlock bb, SSACFG cfg, HashSet<ISSABasicBlock> mbbInLoop){
		for(ISSABasicBlock ibb : cfg.getNormalSuccessors(bb)){
			if(mbbInLoop.contains(ibb))
				return true;
		}
		return false;
	}
}
