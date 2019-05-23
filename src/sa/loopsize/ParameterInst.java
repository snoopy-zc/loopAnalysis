package sa.loopsize;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSACFG;
import com.ibm.wala.ssa.SSAFieldAccessInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.types.ClassLoaderReference;

import sa.wala.IRUtil;

public class ParameterInst {
	int paraLoc; //parameter location 
	//Map<SSAInstruction,CGNode> calleeLoc;// record all location which this function is called 
	//it's possible two SSA Instruction has same 
	public HashSet<LabelledSSA> calleeLoc;
	CallGraph cg;
	CGNode cgNode; // CGNode of whichFun
	recorderHelper rHelper;
	int level = -1; // caller level 
	boolean isStatic = false;
	HashSet<MyPair> whoHasRun;

	public ParameterInst(int anchor, CallGraph myCg, CGNode cgN, HashSet<MyPair> whoHasRun){
		this.cg = myCg;
		this.paraLoc = anchor;
		this.cgNode = cgN;
		//this.calleeLoc = new HashMap<SSAInstruction,CGNode>();
		this.calleeLoc = new HashSet<LabelledSSA>();
		this.isStatic = cgN.getMethod().isStatic();
		this.whoHasRun = whoHasRun;
	}
	
	public ParameterInst(int anchor,CallGraph myCg,CGNode cgN, int level,HashSet<MyPair>whoHasRun){
		this.cg = myCg;
		this.paraLoc = anchor;
		this.cgNode = cgN;
		//this.calleeLoc = new HashMap<SSAInstruction,CGNode>();
		this.calleeLoc = new HashSet<LabelledSSA>();
		this.level = level;
		this.isStatic = cgN.getMethod().isStatic();
		this.whoHasRun = whoHasRun;
	}
	// get the caller of the method where ssaInst is in 
	public void getPossibleCaller(){
		getPossibleCallerCandiate();
		//if(this.cgNode.getMethod().getName().toString().equals("main"))
		//	return;
		//if(this.cgNode.getMethod().getName().toString().contains(".main("))
		//	return;
		
		if(isDerivedThreadRun(this.cgNode)){
			System.out.println("yes......thread create.....");
			//assert 0 == 1;
			findInWhoHasRun(this.whoHasRun,this.cgNode);
			return;
		}
		
		for(Iterator<CGNode> it = this.cg.getPredNodes(this.cgNode);it.hasNext();){
			CGNode tmp_cg = it.next();
			if(tmp_cg.getMethod().getName().toString().equals("fakeRootMethod"))
				continue;
			//System.out.println("xxxxxxxxxxxxxxxxxx" + tmp_cg.getMethod().getName().toString());
	      	if (!LoopVarUtil.isApplicationMethod(this.cgNode))
	      		continue;
	      	if (LoopVarUtil.isNativeMethod(this.cgNode))
	      		continue;
			if(isCallRelation(tmp_cg,this.cgNode)){ // tmp_cg call this.cgNode's method
				if(SSAUtil.otherFileSystem(tmp_cg))
					continue;
				/*if(tmp_cg.getMethod().getDeclaringClass().getName().toString().contains("Ljava/io/") || 
				 tmp_cg.getMethod().getDeclaringClass().getName().toString().contains("Lorg/apache/hadoop/io"))
					continue;
				if(ModelLibrary.libInModelLib(tmp_cg.getMethod().getDeclaringClass().getName().toString()))
					continue;*/
				//System.out.println("???????????????????" + tmp_cg.getMethod().getSignature().toString());
				for(SSAInstruction ret : this.rHelper.whichSSA){
					//System.out.println("wtf???????");
					//System.out.println(tmp_cg.getMethod().getName().toString());
					///System.out.println(ret.toString());
					//if(this.calleeLoc.get(ret) == null){
					LabelledSSA labelledTmp= new LabelledSSA(ret,this.rHelper.origCg);
					if(!SSAUtil.isAlreadyLabelled(labelledTmp,this.calleeLoc)){
						if(LoopVarUtil.isApplicationMethod(this.rHelper.origCg) && !LoopVarUtil.isNativeMethod(this.rHelper.origCg))
							//this.calleeLoc.put(ret,this.rHelper.origCg);
							this.calleeLoc.add(labelledTmp);
						else{
							//TODO, handle Runnable,Callable,etc 
							//System.out.println("sbbbbbbbbbbbbbbbb" + this.rHelper.origCg.getMethod().getDeclaringClass().getClassLoader().getReference().toString());
							if(this.rHelper.origCg.getMethod().getDeclaringClass().getClassLoader().getReference().equals(ClassLoaderReference.Primordial)){
								System.out.println("what happended.....");
								System.out.println(this.cgNode.getMethod().getName().toString());
								System.out.println("call instruction is " + " : " + ret.toString());
								System.out.println("in which class" + " : " + this.rHelper.origCg.getMethod().getDeclaringClass().getName().toString());
								System.out.println("in which method" + " : " + this.rHelper.origCg.getMethod().getName().toString());
								//System.out.println("line number" + ":" + IRUtil.getSourceLineNumberFromSSA(this.rHelper.origCg,ret));
								//assert 0 == 1;
							}else{
								System.out.println("corner case in parameterinst");
								assert 0 == 1;
							}
						}
					}
				}
			}
		}
	}
	
	public static boolean isDerivedThreadRun(CGNode cgn){
		//System.out.println("find all super classes for " + cgn1.getMethod().getDeclaringClass().getName().toString());
	    if(!cgn.getMethod().getName().toString().equals("run") ||
	    		!cgn.getMethod().getReturnType().getName().toString().equals("V")){
	    	return false;
	    }
		HashSet<IClass> allSuperClass = new HashSet<IClass>();
		IClass tmp = cgn.getMethod().getDeclaringClass();
		LinkedList<IClass> work_list = new LinkedList<IClass>();
		work_list.add(tmp);
		allSuperClass.add(tmp);
		while(!work_list.isEmpty()){
			IClass new_class = work_list.poll().getSuperclass();
			if(new_class != null && !allSuperClass.contains(new_class)){
				allSuperClass.add(new_class);
				//System.out.println("find one super class" + new_class.getName().toString());
			}
		}
		
		for(IClass x : allSuperClass){
			//System.out.println(x.getName().toString());
			if(x.getName().toString().contains("Ljava/lang/Thread")){ 
				return true;
			}
		}
		for(IClass y : tmp.getAllImplementedInterfaces()){
			//System.out.println(y.getName().toString());
			if(y.getName().toString().contains("Ljava/lang/Runnable")){ 
				return true;
			}
		}
		return false;
	}
		
	public static HashSet<MyPair> getCaller4ThreadRun(CallGraph cg){
	 	//HashSet<CGNode> whoHasRun = new HashSet<CGNode>();
	 	HashSet<MyPair> caller4run = new HashSet<MyPair>();
		for (Iterator<? extends CGNode> it = cg.iterator(); it.hasNext(); ) {
    		CGNode f = it.next();
	      	if ( LoopVarUtil.isApplicationMethod(f) ) {
	      		if ( !LoopVarUtil.isNativeMethod(f)){
	      			 SSACFG cfg = f.getIR().getControlFlowGraph();
	      			    for (Iterator<ISSABasicBlock> cfg_it = cfg.iterator(); cfg_it.hasNext(); ) {
	      			    	ISSABasicBlock bb = cfg_it.next();
	      			    	for(Iterator<SSAInstruction> bb_it = bb.iterator(); bb_it.hasNext();){
	      			    		SSAInstruction ssaInst = bb_it.next();
	      			    		if(ssaInst instanceof SSAInvokeInstruction){
	      			    			if(((SSAInvokeInstruction)ssaInst).getDeclaredTarget().getDeclaringClass().getName().toString().equals("Ljava/lang/Thread") &&
	      								((SSAInvokeInstruction)ssaInst).getDeclaredTarget().getName().toString().equals("<init>") 
	      							  ){
	      			    				System.out.println(ssaInst.toString());
	      			    				if(ssaInst.getNumberOfUses() >= 2){
	      			    					SSAInstruction ssa =SSAUtil.getSSAByDU(f,ssaInst.getUse(1));
	      			    					if(ssa != null && ssa instanceof SSANewInstruction){
	      			    						String s = ((SSANewInstruction)ssa).getNewSite().getDeclaredType().getName().toString();
	      			    						System.out.println(s);
	      			    						MyPair tmp = new MyPair(s,ssaInst,f);
	      			    						caller4run.add(tmp);
	      			    					}
	      			    				}
	      			    				//System.out.println(((SSAInvokeInstruction)ssaInst).getNumberOfParameters());
	      			    				//whoHasRun.add(f);
	      			    				//return dataSet;
	      							}
	      			    		}
	      			    	}
	      			   }
	      		}
	      	}
	    }
		return caller4run;
	}
	
	public void findInWhoHasRun(HashSet<MyPair> candidate, CGNode compare){
		for(MyPair x : candidate){
			if(x.getClassName() == compare.getMethod().getName().toString()){
				LabelledSSA labelledTmp= new LabelledSSA(x.getSSA(),x.getCGN());
				if(!SSAUtil.isAlreadyLabelled(labelledTmp,this.calleeLoc)){
					if(LoopVarUtil.isApplicationMethod(x.getCGN()) && !LoopVarUtil.isNativeMethod(x.getCGN()))
						this.calleeLoc.add(labelledTmp);
				}
			}
		}
	}
	
	//handle  46   v25 = invokestatic < Application, Lorg/apache/hadoop/hdfs/server/datanode/FSDataset, access$100([Ljava/io/File;Ljava/io/File;)J > v18,v23 @72 except       ion:v24(line 271) {18=[blockFiles]}

	public void getPossibleCallerCandiate(){
		//if(this.cgNode.getMethod().getName().toString().equals("main"))
		//	return;
		//if(this.cgNode.getMethod().getName().toString().contains(".main("))
		//	return;
		for(Iterator<CGNode> it = this.cg.getPredNodes(this.cgNode);it.hasNext();){
			CGNode tmp_cg = it.next();
			//System.out.println("xxxxxxxxxxxxxxxxxx" + tmp_cg.getMethod().getName().toString());
			if(tmp_cg.getMethod().getName().toString().equals("fakeRootMethod"))
				continue;
	      	if (!LoopVarUtil.isApplicationMethod(this.cgNode))
	      		continue;
	      	if (LoopVarUtil.isNativeMethod(this.cgNode))
	      		continue;
			if(isCallRelation(tmp_cg,this.cgNode)){ // tmp_cg call this.cgNode's method
				if(this.isStatic){
					if(tmp_cg.getMethod().getName().toString().contains("access$"))
						this.cgNode = tmp_cg;
				}
			}
		}
	}
	
	
	
	// does CGNode from have call instruction calling to? 
	// from(){ to()};
	public boolean isCallRelation (CGNode from, CGNode to){
		Map<SSAInstruction,Set<CGNode>> recorder = new HashMap<SSAInstruction,Set<CGNode>>();
		this.rHelper = new recorderHelper(from);
		SSAInstruction[] searchSpace = from.getIR().getInstructions();
		for(SSAInstruction inst : searchSpace){
			if(inst instanceof SSAInvokeInstruction){
				//if(from.getMethod().getSignature().toString().contains("main"))
				//	System.out.println(inst.toString());
				Set<CGNode> tmp = this.cg.getPossibleTargets(from, ((SSAInvokeInstruction) inst).getCallSite());
				//for(CGNode x: tmp){
				//	System.out.println("call instruction" + inst.toString());
				//	System.out.println("getPossibleTargets" + ":" + x.getMethod().getSignature().toString());
				//}
				this.rHelper.addMap(inst, tmp);
			}
		}
		return this.rHelper.isContained(to);
	}
	
	public void prune4Loop(){
		HashSet<LabelledSSA> afterPrune = new HashSet<LabelledSSA>();
		System.out.println("prune4Loop......");
		//System.out.println(this.calleeLoc.size());
		for(LabelledSSA ssa : this.calleeLoc){
			//System.out.println("sbbbbbbbbbbbbbbbbbbbbbbbbbb");
			//System.out.println(ssa.getCGNode().getMethod().getName().toString());
			//System.out.println(this.cgNode.getMethod().getName().toString());
			//System.out.println(ssa.getSSA().toString());
			//System.out.println(SSAUtil.findInitializationInBetween(ssa.getCGNode(),this.cgNode,ssa.getSSA()));
			if(!(SSAUtil.findInitializationInBetween(ssa.getCGNode(),this.cgNode,ssa.getSSA()) && SSAUtil.hasFieldVar(this.cgNode,this.paraLoc))){
				afterPrune.add(ssa);
			}
		}
		this.calleeLoc.clear();
		this.calleeLoc.addAll(afterPrune);
		//System.out.println(this.calleeLoc.size());
		//assert 0==1;
	}
	
	public void printCalleeLoc(){
		System.out.println("print callsite information for" + ":" + this.cgNode.getMethod().getSignature().toString());
		for(LabelledSSA tmp : this.calleeLoc){
			SSAInstruction key = tmp.getSSA();
			CGNode value = tmp.getCGNode();
			System.out.println("********************************");
			System.out.println("call instruction is " + " : " + key.toString());
			System.out.println("in which class" + " : " + value.getMethod().getDeclaringClass().getName().toString());
			System.out.println("in which method" + " : " + value.getMethod().getName().toString());
			System.out.println("line number" + ":" + IRUtil.getSourceLineNumberFromSSA(value, key));
			System.out.println("********************************");
		}
	}
}


//record helper class 
class recorderHelper{
	Map<SSAInstruction, Set<CGNode> >recorder;
	CGNode origCg;
	Set<SSAInstruction> whichSSA = new HashSet<SSAInstruction>();
	public recorderHelper(CGNode cgN){
		this.recorder = new HashMap<SSAInstruction,Set<CGNode>>();
		this.origCg = cgN;
	}
	
	public void addMap(SSAInstruction inst, Set<CGNode> cgSet){
		this.recorder.put(inst,cgSet);
	}
	
	public boolean isContained(CGNode targetCGN){
		boolean retVal = false;
		Iterator<Map.Entry<SSAInstruction,Set<CGNode>>> entries = this.recorder.entrySet().iterator();
		while(entries.hasNext()){
			Map.Entry<SSAInstruction,Set<CGNode>> entry = entries.next();
			SSAInstruction key = (SSAInstruction)entry.getKey();
			Set<CGNode> value = (Set<CGNode>)entry.getValue();
			if(value.contains(targetCGN)){
				this.whichSSA.add(key);
				retVal = true;
			}
		}
		return retVal;
	}
}
