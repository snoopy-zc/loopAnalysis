package sa.loopsize;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSACFG;
import com.ibm.wala.ssa.SSAFieldAccessInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;

import sa.loop.LoopAnalyzer;

public class CollectionViaPara {
	CallGraph cg;
	CGNode cgn;
	int para_loc;  // v?
	CollectionInst collInst;
	LoopAnalyzer looper;
	HashSet<MyTriple> retLoops;
	int lineNo = -1;
	HashSet<LabelledSSA> callSiteInfo;
	String txtPath;
	HashSet<MyPair> whoHasRun;
	
	public CollectionViaPara(int loc, CallGraph cg, CGNode cgn, LoopAnalyzer looper, String txtPath, HashSet<MyPair>whoHasRun) {
		// TODO Auto-generated constructor stub
		this.para_loc = loc;
		this.cg = cg;
		this.cgn = cgn;
		this.looper = looper;
		this.retLoops = new HashSet<MyTriple>();
		this.callSiteInfo = new HashSet<LabelledSSA>();
		this.txtPath = txtPath;
		this.whoHasRun = whoHasRun;
	}

	public void doWork(){
		System.out.println("doWork in COllectionViaPara");
		//findAllLoopsInCGN(this.cgn,para_loc);
		findAllLoopsInCallee();
		if(this.callSiteInfo.size() != 0){
			for(LabelledSSA x : this.callSiteInfo){
				CGNode cgn_tmp = x.getCGNode();
				SSAInstruction ssa_tmp = x.getSSA();
				System.out.println("find loop for call site" + ssa_tmp.toString());
				findAllLoopsForCallSite(cgn_tmp,-1,ssa_tmp,x.getParent());
			}
		}
	}
	
	public void findAllLoopsInCGN(CGNode cgn, int para_loc){
		System.out.println("v" + para_loc + "in find all looops in cgn" + cgn.getMethod().getName().toString());
		
		collInst = new CollectionInst(this.cg, cgn,para_loc, this.looper,this.txtPath,this.whoHasRun);
		collInst.findAllModifyLoc(true);
		SSAInstruction my_ssa = null;
		if(collInst.fieldInsts.size() != 0){ 
			System.out.println("find collection operation in collectionViaPara");
			for(LabelledSSA ssa_tmp : collInst.fieldInsts){
				my_ssa = ssa_tmp.getSSA();
				CGNode value = ssa_tmp.getCGNode();
				collInst.findLoopsInCGN(value,my_ssa,ssa_tmp.getParent());
			}
				//if(callerOrNot){
				//	collInst.findLoopsInCaller(cgn,my_ssa, 0);
				//}	
			if(collInst.loopCond.size() != 0){
				System.out.println("find loop in collectionViaPara");
				for(MyTriple x : collInst.loopCond)
					System.out.println(x.getSSA());
			}
			SSAUtil.putNewlyCGN(this.retLoops,collInst.loopCond);
		}
		
	}
	
	public void findAllLoopsForCallSite(CGNode cgn, int para_loc, SSAInstruction callSite,CGNode parentCGN){
		CollectionInst collInst_local = new CollectionInst(this.cg,cgn, para_loc,this.looper,this.txtPath,this.whoHasRun);
		System.out.println(cgn.getMethod().getName().toString());
		System.out.println(this.cgn.getMethod().getName().toString());
		collInst_local.findLoopsInCGN(cgn, callSite,parentCGN);
		if(collInst_local.loopCond.size() != 0){
			System.out.println("find loop in collectionViaPara for call site");
			for(MyTriple x : collInst_local.loopCond)
				System.out.println(x.getSSA());
			SSAUtil.putNewlyCGN(this.retLoops,collInst_local.loopCond);
		}
	}
	
	public void findAllLoopsInCallee(){
		LinkedList<LabelledSSA> nowRet = findAllCallee4Collection(this.cgn,this.para_loc,0);
		while(nowRet.size()!=0){
			LabelledSSA work_ssa = nowRet.poll();
			SSAInstruction ssa_tmp = work_ssa.getSSA();
			CGNode cgn = work_ssa.getCGNode();
			int level_callee = work_ssa.getLevel();
			int para_loc = work_ssa.getPara();
			if(level_callee > 5)
				continue;
			findAllLoopsInCGN(cgn,para_loc);
			System.out.println(para_loc);
			//System.out.println(SSAUtil.getArgument(cgn, para_loc));
			LinkedList<LabelledSSA> ret_tmp = findAllCallee4Collection(cgn,para_loc,++level_callee);
			System.out.println("how many in callee" + ret_tmp.size());
			for(LabelledSSA new_label : ret_tmp){
				if(!SSAUtil.isAlreadyLabelled(new_label, nowRet))
					nowRet.add(new_label);
			}
			System.out.println("find all loops in callee");
		}
	}
	
	public LinkedList<LabelledSSA> findAllCallee4Collection(CGNode cgn, int index, int current_level){
		//System.out.println("what's the index" + index);
		//System.out.println(cgn.getMethod().getName().toString());
		LinkedList<LabelledSSA> ret = new LinkedList<LabelledSSA>();
		SSACFG cfg = cgn.getIR().getControlFlowGraph();
 	    for (Iterator<ISSABasicBlock> ibb = cfg.iterator(); ibb.hasNext(); ) {
 	      ISSABasicBlock bb = ibb.next();
 	      for (Iterator<SSAInstruction> issa = bb.iterator(); issa.hasNext(); ) {
 	    	  SSAInstruction tmp_ssa = issa.next();
 	    	  if(tmp_ssa instanceof SSAInvokeInstruction){
 	    		  if(((SSAInvokeInstruction) tmp_ssa).getDeclaredTarget().getDeclaringClass().getName().toString().contains("Ljava/"))
 	    			  continue;
 	    		  //System.out.println(tmp_ssa.toString());
 	    		  //System.out.println(index);
 	    		  //for(int z = 0; z < tmp_ssa.getNumberOfUses(); z++)
 	    		   //	  System.out.println(tmp_ssa.getUse(z));
 	    		  int numOfUse = tmp_ssa.getNumberOfUses();
 	    		  for(int i = 0 ; i < numOfUse; i++){
 	    			  if(tmp_ssa.getUse(i) == index){
 	    				 // assert 0 == 1;
 	    				//  System.out.println(tmp_ssa.toString());
 	  					  Set<CGNode> tmp = this.cg.getPossibleTargets(cgn, ((SSAInvokeInstruction)tmp_ssa).getCallSite());
 	  					  for(CGNode callee : tmp){
 	  						  int my_para;
 	  						  if(((SSAInvokeInstruction) tmp_ssa).isStatic())
 	  							  my_para = i + 1; //use(0) --> first parameter
 	  						  else
 	  							  my_para = i + 1; //use(1) --> first parameter ---> but v1 is this
 	  						  LabelledSSA new_ssa = new LabelledSSA(tmp_ssa,callee,++current_level,my_para); //ssa doesn't belong to this cgnode
 	  						  //new_ssa.setParent(cgn);
 	  						  if(!SSAUtil.isAlreadyLabelled(new_ssa,ret)){
 	  							  System.out.println("find one potential callee");
 	  							  System.out.println(tmp_ssa.toString());
 	  							  System.out.println(callee.getMethod().getName().toString());
 	  							  System.out.println("v?" + my_para);
 	  							  ret.add(new_ssa);
 	  						  }
 	  						  LabelledSSA call_site = new LabelledSSA(tmp_ssa,cgn);
 	  						  call_site.setParent(callee);
 	  						  if(!SSAUtil.isAlreadyLabelled(call_site, this.callSiteInfo)){
 	  							  this.callSiteInfo.add(call_site);
 	  						  }
 	    				  }
 	    			  }
 	    		  }
 	    	  }
 	       }
 	    }
 	    return ret;
	}
}
