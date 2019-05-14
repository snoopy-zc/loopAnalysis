package sa.loopsize;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSACFG;
import com.ibm.wala.ssa.SSAFieldAccessInstruction;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAPutInstruction;

import sa.wala.IRUtil;

public class FieldInst {
	SSAInstruction fieldAcc; // field access instruction 
	//HashSet<Entry<CGNode,SSAFieldAccessInstruction>> modifyLoc = new HashSet<Entry<CGNode,SSAFieldAccessInstruction>>();// record all location which modify this field access 
	//Map<SSAInstruction,CGNode> modifyLoc; //record all location which modify this field access
	HashSet<LabelledSSA> modifyLoc;
	CallGraph cg;
	String className;
	String varName;
	boolean doSearch;
	CGNode cgNode;
	
	public FieldInst(SSAInstruction inst, CallGraph iCg, CGNode cgN){
		this.fieldAcc = inst;
		this.cg = iCg;
		//this.modifyLoc = new HashMap<SSAInstruction,CGNode>();
		this.modifyLoc = new HashSet<LabelledSSA>();
		this.className = getClass(inst);
		this.varName = getVar(inst);
		this.cgNode = cgN;
		this.doSearch = false;
	}
	
	//for a field instruction, a.b, get class name of a
	public String getClass(SSAInstruction inst){
		String ret =  SSAUtil.fieldOfWhichClass((SSAFieldAccessInstruction)inst);
		System.out.println("class:" + ret);
		return ret;
	}
	
	//for a field instruction, a.b, get name of b  
	public String getVar(SSAInstruction inst){
		String ret = SSAUtil.fieldOfWhichVar((SSAFieldAccessInstruction)inst);
		System.out.println("var:" + ret);
		return ret;
	}
	
	public int getFieldVarType(){
		// return 1, this.field
		// return 2, parameter.field
		// return 3, field of local obj
		// return 4, this.field.field
		if(this.fieldAcc instanceof SSAFieldAccessInstruction){
			if(this.fieldAcc instanceof SSAGetInstruction){
				int tmp = this.fieldAcc.getUse(0);
				if(this.cgNode.getMethod().isStatic()){
					// then it should getstatic instruciton 
					if(this.cgNode.getMethod().getName().toString().contains("access$") && tmp == 1)
						return 1;
						
				}else{
					if(tmp == 1)
						return 1;
				}
				if(SSAUtil.isVnParameter(this.cgNode, tmp)){
					return 2;
				}
			}
		}
		return -1;
	}
	
	//get all modify location of this field 
	public void getAllModifyLoc(){
		//if(getFieldVarType() == 1){
			for (Iterator<? extends CGNode> it = cg.iterator(); it.hasNext();) {
		    	CGNode tmp_n = it.next();
		    	if(tmp_n == null)
		    		continue;
		    	if (!LoopVarUtil.isApplicationMethod(tmp_n))
		    		continue;
		      	if (LoopVarUtil.isNativeMethod(tmp_n))
		      		continue;
		    	//System.out.println(tmp_n.toString());
		    	IMethod tmp_meth = tmp_n.getMethod();
		    	if(tmp_meth == null)
		    		continue;
		    	//System.out.println(tmp_meth.toString());
		    	IR tmp_ir = tmp_n.getIR();
		    	if(tmp_ir == null)
		    		continue;
		    	if(SSAUtil.otherFileSystem(tmp_n))
		    		continue;
		    	//System.out.println(tmp_ir.toString());
		    	SSACFG cfg = tmp_ir.getControlFlowGraph();
		 	    for (Iterator<ISSABasicBlock> ibb = cfg.iterator(); ibb.hasNext(); ) {
		 	      ISSABasicBlock bb = ibb.next();
		 	      for (Iterator<SSAInstruction> issa = bb.iterator(); issa.hasNext(); ) {
		 	    	  SSAInstruction tmp_ssa = issa.next();
		 	    	  if(tmp_ssa instanceof SSAFieldAccessInstruction){
		 	    			SSAFieldAccessInstruction finst = (SSAFieldAccessInstruction)tmp_ssa;
		 	    			if(tmp_ssa instanceof SSAPutInstruction){
		 	    				//TODO derived class?
		 	    				/*System.out.println(finst.toString());
		 	    				System.out.println(SSAUtil.fieldOfWhichClass(finst));
		 	    				System.out.println(SSAUtil.fieldOfWhichVar(finst));
		 	    				System.out.println("xxxxxxxxxxxxxxxxxxx");
		 	    				System.out.println(this.className);
		 	    				System.out.println(this.varName);*/
		 	    				if(SSAUtil.fieldOfWhichClass(finst).equals(this.className) && SSAUtil.fieldOfWhichVar(finst).equals(this.varName)){
		 	    					//System.out.println("fxxxxk"+tmp_ssa.toString());
		 	    					LabelledSSA newTmp = new LabelledSSA(tmp_ssa,tmp_n);
		 	    					if(!SSAUtil.isAlreadyLabelled(newTmp, this.modifyLoc))
		 	    						this.modifyLoc.add(newTmp);
		 	    					//Entry<CGNode,SSAFieldAccessInstruction> newLoc = new java.util.AbstractMap.SimpleEntry<>(tmp_n,finst);
		 	    					//if(!this.modifyLoc.contains(newLoc))
		 	    					//	this.modifyLoc.add(newLoc);
		 	    				}
		 	    			}  
		 	    	  }
		 	      }
		 	    }
		    }
		//}
		this.doSearch = true;
	}
	
	public void printAllModifyLoc(){
		if(this.doSearch != true){
			System.out.println("please invoke getAllModifyLoc first");
			assert (this.doSearch != false);
		}
		if(this.modifyLoc.isEmpty())
			System.out.println("no other modify location for this field:" + this.className + "." + this.varName);
		else{
			System.out.println("begin print modification location for field" + this.fieldAcc.toString());
			for(LabelledSSA entry : this.modifyLoc){
				SSAInstruction key = entry.getSSA();
				CGNode value = entry.getCGNode();
				System.out.println("********************************");
				System.out.println("field instruction is " + ":" + key.toString());
				System.out.println("in which class" + ":" + value.getMethod().getDeclaringClass().getName().toString());
				System.out.println("in which method" + ":" + value.getMethod().getName().toString());
				System.out.println("line number" + ":" + IRUtil.getSourceLineNumberFromSSA(value,key));
				System.out.println("********************************");
			}
			System.out.println("end print");
		}
	}
}
