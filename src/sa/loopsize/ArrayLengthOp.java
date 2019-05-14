package sa.loopsize;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.shrikeBT.IBinaryOpInstruction;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSABinaryOpInstruction;
import com.ibm.wala.ssa.SSACFG;
import com.ibm.wala.ssa.SSAConditionalBranchInstruction;
import com.ibm.wala.ssa.SSAFieldAccessInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.ssa.SSAPhiInstruction;
import com.ibm.wala.ssa.SSAPutInstruction;
import com.ibm.wala.util.graph.NodeManager;

import sa.loop.LoopAnalyzer;
import sa.loop.LoopInfo;
import sa.wala.IRUtil;

public class ArrayLengthOp {
	CGNode cgN;
	SSANewInstruction ssa;
	int index = -1;
	HashSet<SSAInstruction> bopSet;
	LoopAnalyzer looper;
	HashSet<MyTriple> loopCond;
	HashSet<LabelledSSA> vistiedLoopCond; // the loop condition we have visited


	public ArrayLengthOp(CGNode cgn, SSANewInstruction ssa, LoopAnalyzer looper) {
		// TODO Auto-generated constructor stub
		this.cgN = cgn;
		this.ssa = ssa;
		if(ssa != null)
			this.index = this.ssa.getUse(0);
		this.bopSet = new HashSet<SSAInstruction>();
		this.looper = looper;
		this.loopCond = new HashSet<MyTriple>();
		this.vistiedLoopCond = new HashSet<LabelledSSA>();
	}
	
	//find like v24, binaryop(add) v26, v19:#1
	public void findBinaryOp(){
		//System.out.println(SSAUtil.getSSAByDU(this.cgN,this.index).toString());
		SSAInstruction my_tmp = SSAUtil.getSSAByDU(this.cgN,this.index);
		if(my_tmp == null || (my_tmp != null && my_tmp instanceof SSAPhiInstruction)){
	    	SSACFG cfg = this.cgN.getIR().getControlFlowGraph();
			for (Iterator<ISSABasicBlock> ibb = cfg.iterator(); ibb.hasNext(); ) {
		 	      ISSABasicBlock bb = ibb.next();
		 	      for (Iterator<SSAInstruction> issa = bb.iterator(); issa.hasNext(); ) {
		 	    	  SSAInstruction tmp_ssa = issa.next();
		 	    	  if(tmp_ssa instanceof SSABinaryOpInstruction){
		 	    		  /*System.out.println(tmp_ssa.toString());
		 	    		  System.out.println(((SSABinaryOpInstruction) tmp_ssa).getOperator().toString());
		 	    		  System.out.println(tmp_ssa.getUse(0));
		 	    		  System.out.println(this.index);
		 	    		  System.out.println(SSAUtil.isConstant(this.cgN,tmp_ssa.getUse(1)));
		 	    		  System.out.println(SSAUtil.getConstant(this.cgN, tmp_ssa.getUse(1)).toString());
		 	    		  System.out.println(Float.valueOf(SSAUtil.getConstant(this.cgN, tmp_ssa.getUse(1))));*/
		 	    		  if(((SSABinaryOpInstruction) tmp_ssa).getOperator().equals(IBinaryOpInstruction.Operator.ADD)){
		 	    			  if(tmp_ssa.getUse(0) == this.index && SSAUtil.isConstant(this.cgN,tmp_ssa.getUse(1))){
		 	    				  String op2 = SSAUtil.getConstant(this.cgN, tmp_ssa.getUse(1));
		 	    				  if(!op2.equals("null") && Float.valueOf(op2) > 0 )
		 	    					  this.bopSet.add(tmp_ssa);
		 	    			  }
		 	    		  }
		 	    	  }
		 	      }
		 	}

		}
	}

	public void doWork(){
		findBinaryOp();
		if(this.bopSet.size() == 0)
			return;
		List<LoopInfo> allLoops = this.looper.findLoopsForCGNode(this.cgN);
		for(SSAInstruction tmp : this.bopSet){
			findLoopForOpInCGN(allLoops,tmp);
		}
	}
	
	public void findLoopForOpInCGN(List<LoopInfo> allLoops, SSAInstruction ssa_target){
		HashSet<SSAInstruction> branchInst = new HashSet<SSAInstruction>();
		for(LoopInfo tmp_loop : allLoops){
			if(tmp_loop.containsSSA(ssa_target)){
				System.out.println("a loop contains op.add");
				int x = tmp_loop.getBeginBasicBlockNumber();
				ISSABasicBlock bb = IRUtil.getBasicBlock(this.cgN, x);
				ISSABasicBlock relocateBB = SSAUtil.ifHasNextBB(bb,this.cgN.getIR().getControlFlowGraph());
				System.out.println();
				if(relocateBB != null){
					System.out.println("excues me???");
					bb = relocateBB;
				}
				System.out.println();
				if(relocateBB != null){
					System.out.println("excues me???");
					bb = relocateBB;
				}
				int branchNum = 0;
			//	SSAInstruction target = null;
				System.out.println(bb.getNumber());
				for(SSAInstruction ssa_tmp : bb){
					System.out.println("way1" + ssa_tmp.toString());
					if(ssa_tmp instanceof SSAConditionalBranchInstruction){
						if(!branchInst.contains(ssa_tmp))
							branchInst.add(ssa_tmp);
					//	System.out.println("xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" + ssa_tmp.toString());
					}
				}
				
				for(SSAInstruction ssa_tmp : SSAUtil.getInstructionsInSameLine(bb, this.cgN)){
					System.out.println("way2" + ssa_tmp.toString());
					if(ssa_tmp instanceof SSAConditionalBranchInstruction){
						if(!branchInst.contains(ssa_tmp))
							branchInst.add(ssa_tmp);
						//	System.out.println("xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" + ssa_tmp.toString());
					}
				}
				//assert branchNum <= 1;
				if(branchInst.isEmpty()){
					// 1. get from indirect 
					//assert 0 == 1;
					//target = LoopCond.getInDirectBranch(bb,this.cgN);
					//if(target == null){
					// 2. while(true)
						if(LoopCond.isWhileTrueCase(bb,this.cgN)){
							System.out.println("class name" + ":" + this.cgN.getMethod().getDeclaringClass().getName().toString());
							System.out.println("method name" + ":" + this.cgN.getMethod().getName().toString());
							System.out.println("SSAInstruction(op)" + ":" + ssa_target.toString());
							System.out.println("line number" + ":" + IRUtil.getSourceLineNumberFromSSA(this.cgN,ssa_target));
							MyTriple whileTrue = new MyTriple(ssa_target,this.cgN,-2);
							if(!SSAUtil.isAlreadyInTriple(whileTrue,this.loopCond))
								this.loopCond.add(whileTrue);
							//assert 0 == 1;
						}
					//}
				}else{
					for(SSAInstruction target : branchInst){
						LabelledSSA loopTmp = new LabelledSSA(target,this.cgN);
						if(!SSAUtil.isAlreadyLabelled(loopTmp, vistiedLoopCond) && (target!=null)){
							MyTriple z = new MyTriple(target,this.cgN,-1);
							//MyPair z = new MyPair(target,-1);
							System.out.println("what's up in findLoopsInCGN" + target.toString());
							if(!SSAUtil.isAlreadyInTriple(z,this.loopCond))
								this.loopCond.add(z);
							this.vistiedLoopCond.add(loopTmp);
						}
					}
				}
			}
		}
	}
}
