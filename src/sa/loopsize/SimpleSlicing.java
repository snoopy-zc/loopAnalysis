package sa.loopsize;

import java.util.HashSet;
import java.util.LinkedList;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ssa.SSAInstruction;

public class SimpleSlicing {
	//this is naive slicing, only focus on local variable and parameter
	SSAInstruction inst;
	CGNode cgN;
	CallGraph cg;
	HashSet<SSAInstruction> slicing;
	HashSet<CGNode> slicerNode;
	HashSet<MyPair> whoHasRun;
	public SimpleSlicing(SSAInstruction inst,CGNode cgN, CallGraph iCG,HashSet<MyPair>whoHasRun) {
		// TODO Auto-generated constructor stub
		this.inst = inst;
		this.cgN = cgN;
		this.cg = iCG;
		this.slicerNode = new HashSet<CGNode>();
		this.whoHasRun = whoHasRun;
	}

	public HashSet<Integer> getSlicingInFunc(SSAInstruction inst,CGNode cgN, CallGraph cg){
		LinkedList<LabelledSSA> work_set = new LinkedList<LabelledSSA>();
		HashSet<LabelledSSA> visited_set = new HashSet<LabelledSSA>();
		HashSet<LabelledSSA> slicer = new HashSet<LabelledSSA>();
		LabelledSSA inst_tag = new LabelledSSA(inst,cgN);
		HashSet<Integer> ret = new HashSet<Integer>();
		work_set.add(inst_tag);
		visited_set.add(inst_tag);
		slicer.add(inst_tag);
		this.slicerNode.add(cgN);
		while(!work_set.isEmpty()){
			LabelledSSA tmpInst = work_set.poll();
			for(int i = 0; i < tmpInst.getSSA().getNumberOfUses();i++){
				int index = tmpInst.getSSA().getUse(i);
				/*System.out.println(index);
				System.out.println(tmpInst.getSSA().toString());
				System.out.println(tmpInst.getCGNode().getMethod().getName().toString());*/
				SSAInstruction ssa_tmp = SSAUtil.getSSAByDU(tmpInst.getCGNode(),index);
				if(ssa_tmp != null){
					LabelledSSA ssa_tmp_tag = new LabelledSSA(ssa_tmp,tmpInst.getCGNode());
					if(!SSAUtil.isAlreadyLabelled(ssa_tmp_tag, visited_set)){
						slicer.add(ssa_tmp_tag);
						work_set.add(ssa_tmp_tag);
						visited_set.add(ssa_tmp_tag);
					}
				}
				if(ssa_tmp == null && SSAUtil.isVnParameter(tmpInst.getCGNode(),index)){
					ret.add(index);
				}

			}
		}
		if(ret.size() == 0){
			work_set.clear();
			visited_set.clear();
			slicer.clear();
		}
		return ret;
	}
	
	/*public HashSet<LabelledSSA> getSlicingAcrossFunc(int index, CGNode cgN, CallGraph cg){
		HashSet<LabelledSSA> ret = new HashSet<LabelledSSA>();
		ParameterInst paraObj = new ParameterInst(SSAUtil.getParameterLoc(index,cgN),cg,cgN);
		paraObj.getPossibleCaller();
		if(paraObj.calleeLoc.size()!=0){
			for(LabelledSSA para_entry : paraObj.calleeLoc){
				ret.addAll(getSlicingInFunc(para_entry.getSSA(),para_entry.getCGNode(),cg));
				this.slicerNode.add(para_entry.getCGNode());
			}
		}
		return ret;
	}*/
	
	public void getAllNaiveSlicing(SSAInstruction ssa, CGNode cgN, CallGraph cg){
		LinkedList<LabelledSSA> work_set = new LinkedList<LabelledSSA>();
		HashSet<LabelledSSA> visited_set = new HashSet<LabelledSSA>();
		LabelledSSA ssa_tag = new LabelledSSA(ssa,cgN,0);
		work_set.add(ssa_tag);
		visited_set.add(ssa_tag);
		while(!work_set.isEmpty()){
			System.out.println("sbbbbbbbbbbbbbbbbbb.....");
			LabelledSSA work_inst =work_set.poll();
			HashSet<Integer> retInt = getSlicingInFunc(work_inst.getSSA(),work_inst.getCGNode(),cg);
			int tmp_level = work_inst.getLevel() + 1;
			if(retInt.isEmpty() || tmp_level > 20){
				continue;
			}else{
				for(Integer x : retInt){
					ParameterInst paraObj = new ParameterInst(SSAUtil.getParameterLoc(x.intValue(),work_inst.getCGNode()),cg,work_inst.getCGNode(),this.whoHasRun);
					paraObj.getPossibleCaller();
					if(paraObj.calleeLoc.size() != 0){
						for(LabelledSSA para_entry : paraObj.calleeLoc){
							LabelledSSA new_ssa = new LabelledSSA(para_entry.getSSA(),para_entry.getCGNode(),tmp_level);
							if(!SSAUtil.isAlreadyLabelled(new_ssa,visited_set)){
								work_set.add(new_ssa);
								visited_set.add(new_ssa);
							}
						}
					}
				}
			}
		}
	}
}
