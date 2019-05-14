package sa.loopsize;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSACFG;
import com.ibm.wala.ssa.SSAConditionalBranchInstruction;
import com.ibm.wala.ssa.SSAFieldAccessInstruction;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSAPutInstruction;

import sa.loop.LoopAnalyzer;
import sa.loop.LoopInfo;
import sa.wala.IRUtil;

public class CollectionInst {
	SSAInstruction inst;
	CallGraph cg;
	CGNode cgNode;
	String varName;
	String varType;
	String varInClass;
	String txtPath;
	int varIndex = -1;
	//Map<SSAInstruction,CGNode> fieldInsts;
	HashSet<LabelledSSA> fieldInsts;
	HashSet<MyTriple> allFieldInsts;
	HashSet<MyTriple> fieldCollectionInit;
	LoopAnalyzer looper;
	//Map<MyPair,CGNode> loopCond;
	HashSet<MyTriple> loopCond;
	//HashSet<CGNode> visited; // the caller we have visited, avoid recursive function  
	HashSet<LabelledSSA> visited;
	HashSet<LabelledSSA> vistiedLoopCond; // the loop condition we have visited
	SSAInvokeInstruction ssaCall; // collection via callee
	boolean localOrNot = false;
	HashSet<MyPair> whoHasRun;
	// this is for field collection 
	public CollectionInst(CallGraph cgIn, CGNode cgNIn, SSAInstruction ssa, String varName, String varType, String className,LoopAnalyzer looper, String txtPath,HashSet<MyPair>whoHasRun) {
		// TODO Auto-generated constructor stub
		this.inst = ssa;
		this.cg = cgIn;
		this.cgNode = cgNIn;
		this.varName = varName;
		this.varType = varType;
		this.varInClass = className;
		//this.fieldInsts = new HashMap<SSAInstruction,CGNode>();
		this.fieldInsts = new HashSet<LabelledSSA>();
		//this.loopCond = new HashMap<MyPair,CGNode>();
		this.loopCond = new HashSet<MyTriple>();
		this.looper = looper;
		//this.visited = new HashSet<CGNode>();
		//this.vistiedLoopCond = new HashSet<SSAInstruction>();
		this.visited = new HashSet<LabelledSSA>();
		this.vistiedLoopCond = new HashSet<LabelledSSA>();
		this.fieldCollectionInit = new HashSet<MyTriple>();
		this.localOrNot = false;
		this.allFieldInsts = new HashSet<MyTriple>();
		this.txtPath = txtPath;
		this.whoHasRun = whoHasRun;
	}

	// this is for local collection
	public CollectionInst(CallGraph cgIN, CGNode cgNIn,int varIndex, LoopAnalyzer looper,String txtPath,HashSet<MyPair>whoHasRun){
		this.cg = cgIN;
		this.cgNode = cgNIn;
		this.varIndex = varIndex;
		//this.fieldInsts = new HashMap<SSAInstruction,CGNode>();
		this.fieldInsts = new HashSet<LabelledSSA>();
		//this.loopCond = new HashMap<MyPair,CGNode>();
		this.loopCond = new HashSet<MyTriple>();
		this.looper = looper;
		//this.visited = new HashSet<CGNode>();
		this.visited = new HashSet<LabelledSSA>();
		//this.vistiedLoopCond = new HashSet<SSAInstruction>();
		this.vistiedLoopCond = new HashSet<LabelledSSA>();
		this.localOrNot = true;
		this.allFieldInsts = new HashSet<MyTriple>();
		this.txtPath = txtPath;
		this.whoHasRun = whoHasRun;
	}
	
	
	public void findAllModifyLoc(boolean localOrNot){ // for local collection 
		if(localOrNot){
			SSACFG cfg = this.cgNode.getIR().getControlFlowGraph();
		    for (Iterator<ISSABasicBlock> ibb = cfg.iterator(); ibb.hasNext(); ) {
		 	      ISSABasicBlock bb = ibb.next();
		 	      for (Iterator<SSAInstruction> issa = bb.iterator(); issa.hasNext(); ) {
		 	    	  SSAInstruction tmp_ssa = issa.next();	
		 	    	  if(tmp_ssa instanceof SSAInvokeInstruction){
		 	    		  //System.out.println("sbbbbb" + tmp_ssa.toString());
		 	    		  if(SSAUtil.isCollectionMethod(tmp_ssa)){
		 	    			   System.out.println("is collection method???");
		 	    			   System.out.println(this.varIndex);
		 	    			   System.out.println(tmp_ssa.toString());
		 	    			// System.out.println(tmp_ssa.getUse(0));
		 	    			  if(tmp_ssa.getUse(0) == this.varIndex){
		 	    				  //System.out.println(tmp_ssa.toString());
		 	    				  //System.out.println(this.varIndex);
		 	    				  //assert 0==1;
		 	    				  LabelledSSA fieldTmp = new LabelledSSA(tmp_ssa,this.cgNode);
		 	    				  if(!SSAUtil.isAlreadyLabelled(fieldTmp,this.fieldInsts))
		 	    				  //this.fieldInsts.put(tmp_ssa,this.cgNode);
		 	    					  this.fieldInsts.add(fieldTmp);
		 	    			  }
		 	    		  }
		 	    	  }
		 	    }
		 	}
		 }
	}
	
	//1. get all locations which invoke getfield for this collection (not for local collection)
	//2. determine which actually does add/addAll/put/putAll
	public void findAllModifyLoc(){
		for (Iterator<? extends CGNode> it = this.cg.iterator(); it.hasNext();) {
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
	    	/*if(!tmp_n.getMethod().getName().toString().equals("add"))
	    		continue;
	    	if(!tmp_n.getMethod().getDeclaringClass().getName().toString().equals("Lorg/apache/hadoop/hdfs/server/namenode/JournalSet"))
	    		continue;
	    	System.out.println(tmp_n.getMethod().getName().toString());
	    	System.out.println(tmp_n.getMethod().getDeclaringClass().getName().toString());
	    	System.out.println(tmp_n.getMethod().getDeclaringClass().getName().getClassName().toString());
	    	System.out.println(this.varInClass);
	    	System.out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
	    	System.out.println("xxxxx");*/
	    	//System.out.println(tmp_ir.toString());
	    	if(ModelLibrary.isModeled4Loop(this.txtPath,tmp_n)){
				//System.out.println("the function we modeled for loop");
				continue;
			}
	    	SSACFG cfg = tmp_ir.getControlFlowGraph();
	 	    for (Iterator<ISSABasicBlock> ibb = cfg.iterator(); ibb.hasNext(); ) {
	 	      ISSABasicBlock bb = ibb.next();
	 	      for (Iterator<SSAInstruction> issa = bb.iterator(); issa.hasNext(); ) {
	 	    	  SSAInstruction tmp_ssa = issa.next();
	 	    	  if(tmp_ssa instanceof SSAFieldAccessInstruction){
	 	    			SSAFieldAccessInstruction finst = (SSAFieldAccessInstruction)tmp_ssa;
	 	    			if(tmp_ssa instanceof SSAGetInstruction){
	 	    				//TODO derived class?
	 	    				/*System.out.println("current cgn " + tmp_n.getMethod().getDeclaringClass().getName().toString());
	 	    				System.out.println(this.inst.toString());
	 	    				System.out.println(this.varName);
	 	    				System.out.println(this.varType);
	 	    				System.out.println(this.varInClass);
	 	    				System.out.println("test " + tmp_ssa.toString());
	 	    				System.out.println("test " + ((SSAFieldAccessInstruction) tmp_ssa).getDeclaredField().getName().toString());
	 	    				System.out.println("test " + ((SSAFieldAccessInstruction) tmp_ssa).getDeclaredFieldType().getName().toString());
	 	    				System.out.println("test " + ((SSAFieldAccessInstruction) tmp_ssa).getDeclaredField().getDeclaringClass().getName().toString());
	 	    				//assert 0 == 1;
	 	    				System.out.println("test" + ((SSAFieldAccessInstruction) tmp_ssa).getDeclaredField().getDeclaringClass().getName().toString());*/
	 	    				/*System.out.println(tmp_n.getMethod().getDeclaringClass().getName().toString());
	 	    				System.out.println(this.cgNode.getMethod().getDeclaringClass().getName().toString());
	 	    				System.out.println(SSAUtil.twoClassesDerived(this.cgNode,tmp_n));
	 	    				System.out.println("sbbbbbbbbbbb");*/
	 	    				if(((SSAFieldAccessInstruction) tmp_ssa).getDeclaredField().getName().toString().equals(this.varName) &&
	 	    						((SSAFieldAccessInstruction) tmp_ssa).getDeclaredFieldType().getName().toString().equals(this.varType) &&
	 	    						SSAUtil.twoClassesDerived(this.cgNode,tmp_n)
	 	    						/*&&
	 	    						((SSAFieldAccessInstruction) tmp_ssa).getDeclaredField().getDeclaringClass().getName().toString().equals(this.varInClass)*/){
	 	    					SSAInvokeInstruction myInst = isAddOrPut(tmp_n,cfg,tmp_ssa,this.varType);
	 	    					if( myInst!= null){
	 	    						LabelledSSA fieldGetTmp = new LabelledSSA(myInst,tmp_n);
	 	    						if(!SSAUtil.isAlreadyLabelled(fieldGetTmp,this.fieldInsts))
	 	    						//this.fieldInsts.put(tmp_ssa,tmp_n);
	 	    							this.fieldInsts.add(fieldGetTmp);
	 	    						if(((SSAInvokeInstruction) myInst).getDeclaredTarget().getName().toString().equals("add") ||
	 	    								((SSAInvokeInstruction) myInst).getDeclaredTarget().getName().toString().equals("put")){
		 	    						System.out.println("find one via getfield(put/add)" + myInst.toString());
		 	   
	 	    						}else{
		 	    						System.out.println("find one via getfield(putAll/addAll)" + myInst.toString());
		 	    						SSAInstruction addputAll = SSAUtil.getSSAByDU(tmp_n, myInst.getUse(1));
		 	    						if( addputAll != null){
		 	    							MyTriple fieldAllTmp = new MyTriple(addputAll,tmp_n,-1);
		 	    							if(!SSAUtil.isAlreadyInTriple(fieldAllTmp, this.allFieldInsts))
		 	    								this.allFieldInsts.add(fieldAllTmp);
		 	    						}
	 	    						}
	 	    						
	 	    					}
	 	    				}
	 	    			}else if(tmp_ssa instanceof SSAPutInstruction){
	 	    				if(((SSAFieldAccessInstruction) tmp_ssa).getDeclaredField().getName().toString().equals(this.varName) &&
	 	    						((SSAFieldAccessInstruction) tmp_ssa).getDeclaredFieldType().getName().toString().equals(this.varType) &&
	 	    						SSAUtil.twoClassesDerived(this.cgNode,tmp_n)
	 	    						/*&&
	 	    						((SSAFieldAccessInstruction) tmp_ssa).getDeclaredField().getDeclaringClass().getName().toString().equals(this.varInClass)*/){
	 	    						System.out.println("find one via putfield" + tmp_ssa.toString());
	 	    						MyTriple fieldPutTmp = new MyTriple(tmp_ssa,tmp_n,-1);
	 	    						if(!SSAUtil.isAlreadyInTriple(fieldPutTmp,this.fieldCollectionInit))
	 	    						//this.fieldInsts.put(tmp_ssa,tmp_n);
	 	    							this.fieldCollectionInit.add(fieldPutTmp);
	 	    				}
	 	    			}  
	 	    	  }
	 	      }
	 	    }
	    }
	}
	
	public SSAInvokeInstruction isAddOrPut(CGNode cgN, SSACFG cfg,SSAInstruction ssa, String type){
		SSAInstruction tmp = null;
		SSAInvokeInstruction retInst = null;
 	    for (Iterator<ISSABasicBlock> ibb = cfg.iterator(); ibb.hasNext(); ) {
	 	      ISSABasicBlock abb = ibb.next();
	 	      for (Iterator<SSAInstruction> issa = abb.iterator(); issa.hasNext(); ) {
	 	    	  SSAInstruction tmp_ssa = issa.next();
	 	    	  if(tmp_ssa instanceof SSAInvokeInstruction){
	 	    		 // System.out.println("isAddorput " + cgN.getMethod().getName().toString());
	 	    		  //System.out.println("isAddorput " + tmp_ssa.toString());
	 	    		  //System.out.println(ssa.toString());
	 	    		  //System.out.println(tmp_ssa.toString());
	 	    		  //System.out.println(tmp_ssa.getNumberOfUses());
	 	    		  //System.out.println(tmp_ssa.getUse(0));
	 	    		  //tmp = SSAUtil.getSSAIndexByDefvn(cfg.getInstructions(),tmp_ssa.getUse(0));
	 	    		  if(tmp_ssa.getNumberOfUses() == 0)
	 	    			  continue;
	 	    		  tmp = SSAUtil.getSSAByDU(cgN,tmp_ssa.getUse(0));
	 	    		  //if(tmp != null){
	 	    		//	  System.out.println("potential" + tmp.toString());
	 	    		//	  System.out.println(((SSAInvokeInstruction) tmp_ssa).getDeclaredTarget().getDeclaringClass().getName().toString());
	 	    		 // }
	 	    		  if(tmp != null && tmp.equals(ssa)){
	 	    			  if(((SSAInvokeInstruction) tmp_ssa).getDeclaredTarget().getDeclaringClass().getName().toString().equals(type)){
	 	    				  if(((SSAInvokeInstruction) tmp_ssa).getDeclaredTarget().getName().toString().equals("add") ||
	 	    						 ((SSAInvokeInstruction) tmp_ssa).getDeclaredTarget().getName().toString().equals("addAll") ||
	 	    						((SSAInvokeInstruction) tmp_ssa).getDeclaredTarget().getName().toString().equals("put") ||
	 	    						((SSAInvokeInstruction) tmp_ssa).getDeclaredTarget().getName().toString().equals("putAll")){
	 	    					  retInst = (SSAInvokeInstruction) tmp_ssa;
	 	    					  break;
	 	    				  }
	 	    			  }
	 	    		  }
	 	    	  }
	 	     }
 	    }
 	    return retInst;
	}
	
	// whether this field instruction in a loop 
	public boolean findLoopsInCGN(CGNode currentCGN, SSAInstruction ssa, CGNode parentCGN){
		//System.out.println("find loops in..." + ssa.toString());
		//System.out.println("find loops in..." + currentCGN.getMethod().getName().toString());
		
		if(ModelLibrary.isModeled4Loop(this.txtPath,currentCGN)){
			//System.out.println("the function we modeled for loop");
			return true;
		}
		
		System.out.println("find loops in cgn" + ":" + currentCGN.getMethod().getName().toString());
		System.out.println("ssa is" + ":" + ssa.toString());
		//if(this.visited.contains(currentCGN))
		//	return;
		//else{
		//	this.visited.add(currentCGN);
		//}
		HashSet<SSAInstruction> branchInst = new HashSet<SSAInstruction>();
		LabelledSSA myTmp = new LabelledSSA(ssa,currentCGN);
		if(!SSAUtil.isAlreadyLabelled(myTmp, this.visited))
			this.visited.add(myTmp);
		else
			return false;
		System.out.println("begin finding...");
		List<LoopInfo> allLoops = this.looper.findLoopsForCGNode(currentCGN);
		for(LoopInfo tmp_loop : allLoops){
			if(tmp_loop.containsSSA(ssa)){
//				System.out.println(parentCGN.toString());
				if(parentCGN != null && SSAUtil.findInitializationInBetween(currentCGN,parentCGN,ssa)){
					//System.out.println(currentCGN.getMethod().getName().toString());
					//System.out.println(parentCGN.getMethod().getName().toString());
					//assert 0==1;
					continue;
				}
				System.out.println("a loop contains it.....");
				int x = tmp_loop.getBeginBasicBlockNumber();
				ISSABasicBlock bb = IRUtil.getBasicBlock(currentCGN, x);
				ISSABasicBlock relocateBB = SSAUtil.ifHasNextBB(bb,currentCGN.getIR().getControlFlowGraph());
				System.out.println(bb.toString());
				if(relocateBB != null){
					System.out.println("excues me???");
					bb = relocateBB;
				}
				System.out.println(bb.getNumber());
				
				for(SSAInstruction ssa_tmp : bb){
					System.out.println("way1" + ssa_tmp.toString());
					if(ssa_tmp instanceof SSAConditionalBranchInstruction){
						if(!branchInst.contains(ssa_tmp))
							branchInst.add(ssa_tmp);
					//	System.out.println("xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" + ssa_tmp.toString());
					}
				}
				
				for(SSAInstruction ssa_tmp : SSAUtil.getInstructionsInSameLine(bb, currentCGN)){
					System.out.println("way2" + ssa_tmp.toString());
					if(ssa_tmp instanceof SSAConditionalBranchInstruction){
						if(!branchInst.contains(ssa_tmp))
							branchInst.add(ssa_tmp);
						//	System.out.println("xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" + ssa_tmp.toString());
					}
				}
				if(branchInst.isEmpty()){
					// 1. get from indirect 
					//assert 0 == 1;
					//target = LoopCond.getInDirectBranch(bb,currentCGN);
					//if(target == null){
					// 2. while(true)
						if(LoopCond.isWhileTrueCase(bb,currentCGN)){
							System.out.println("class name" + ":" + currentCGN.getMethod().getDeclaringClass().getName().toString());
							System.out.println("method name" + ":" + currentCGN.getMethod().getName().toString());
							System.out.println("SSAInstruction" + ":" + ssa.toString());
							System.out.println("line number" + ":" + IRUtil.getSourceLineNumberFromSSA(currentCGN,ssa));
							MyTriple whileTrue = new MyTriple(ssa,currentCGN,-2);
							whileTrue.setHasChild(false);
							if(!SSAUtil.isAlreadyInTriple(whileTrue,this.loopCond))
								this.loopCond.add(whileTrue);
							//assert 0 == 1;
						}
					//}
				}else{
					for(SSAInstruction target : branchInst){
						LabelledSSA loopTmp = new LabelledSSA(target,currentCGN);
						if(!SSAUtil.isAlreadyLabelled(loopTmp, vistiedLoopCond) && (target!=null)){
							MyTriple z = new MyTriple(target,currentCGN,-1);
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
		return false;
	}
	
	
	// continue find caller to search loop
	// use myLevel <= int to set how many levels caller we want to analyze 
	public void findLoopsInCaller(CGNode currentCGN,SSAInstruction ssa, int currentLevel){
		if(ModelLibrary.isModeled4Loop(this.txtPath,currentCGN)){
			//System.out.println("the function we modeled for loop");
			return;
		}
		System.out.println("find loops in caller");
		ParameterInst paraObj;
		LinkedList<LabelledSSA> work_set = new LinkedList<LabelledSSA>();
		//this.visited.add(currentCGN);
		LabelledSSA labelTmp = new LabelledSSA(ssa,currentCGN);
		paraObj = new ParameterInst(-1,this.cg,currentCGN,currentLevel,this.whoHasRun);
		paraObj.getPossibleCaller();
		paraObj.printCalleeLoc();
		paraObj.prune4Loop();
		work_set.addAll(SSAUtil.set2LabelledList(paraObj.calleeLoc,++paraObj.level));
		
		while(!work_set.isEmpty()){
			LabelledSSA label_tmp = work_set.poll();
			CGNode cgn_tmp = label_tmp.getCGNode();
			SSAInstruction ssa_tmp = label_tmp.getSSA();
			//System.out.println(cgn_tmp.getGraphNodeId());
			int myLevel = label_tmp.getLevel();
			//System.out.println("wtfffffffff"+myLevel);
			LabelledSSA labelTmp2 = new LabelledSSA(ssa_tmp,cgn_tmp);
			if(!SSAUtil.isAlreadyLabelled(labelTmp2,this.visited) && myLevel <= 10000){
			//if(!this.visited.contains(cgn_tmp) && myLevel <= 10000 ){
			//   if(myLevel <= 10000){
				if(findLoopsInCGN(cgn_tmp,ssa_tmp,currentCGN))
					continue;
				CGNode new_cgn_tmp = null;
				String newClassName = cgn_tmp.getMethod().getDeclaringClass().getName().toString();
				String newMethodName = cgn_tmp.getMethod().getName().toString();
				System.out.println("bingo 0000");
				if(newClassName.contains("$") && 
						newMethodName.equals("run")&&newClassName.split("\\$")[1].matches("\\d+")){
					String name1 = newClassName.split("\\$")[0];
					String name2 = newClassName;
					System.out.println("bingo11111");
					System.out.println(name1);
					System.out.println(name2);
					new_cgn_tmp = SSAUtil.findTrueCaller4Run(this.cg,name1,name2);
				}
				
				if(new_cgn_tmp != null){
					System.out.println("bingo...");
					System.out.println(new_cgn_tmp.getMethod().getName().toString());
					System.out.println(new_cgn_tmp.getMethod().getDeclaringClass().getName().toString());
					paraObj = new ParameterInst(-1,this.cg,new_cgn_tmp,myLevel,this.whoHasRun);
				}
				else
					paraObj = new ParameterInst(-1,this.cg,cgn_tmp,myLevel,this.whoHasRun);
				paraObj.getPossibleCaller();
				paraObj.printCalleeLoc();
				paraObj.prune4Loop();
				work_set.addAll(SSAUtil.set2LabelledList(paraObj.calleeLoc,++paraObj.level));
				//this.visited.add(cgn_tmp);
				this.visited.add(labelTmp2);
			}
		}
	}
	
	public void printModifyLoc(boolean localOrNot){
		if(localOrNot)
			System.out.println("print local collection in CollectionInst(single)");
		else
			System.out.println("print field collection in CollectionInst(single)");
		System.out.println("============================================");
		System.out.println(this.varIndex);
		for(LabelledSSA entry : this.fieldInsts){
			SSAInstruction key = entry.getSSA();
			CGNode value = entry.getCGNode();
			System.out.println("class name" + ":" + value.getMethod().getDeclaringClass().getName().toString());
			System.out.println("method name" + ":" + value.getMethod().getName().toString());
			System.out.println("SSAInstruction" + ":" + key.toString());
			System.out.println("line number" + ":" + IRUtil.getSourceLineNumberFromSSA(value, key));
		}
		System.out.println("============================================");
		if(!this.localOrNot && this.fieldCollectionInit.size() != 0){
			System.out.println("print field collection initialization");
			System.out.println("============================================");
			for(MyTriple entry : this.fieldCollectionInit){
				SSAInstruction key = entry.getSSA();
				CGNode value = entry.getCGNode();
				System.out.println("class name" + ":" + value.getMethod().getDeclaringClass().getName().toString());
				System.out.println("method name" + ":" + value.getMethod().getName().toString());
				System.out.println("SSAInstruction" + ":" + key.toString());
				System.out.println("line number" + ":" + IRUtil.getSourceLineNumberFromSSA(value, key));
			}
			System.out.println("============================================");
		}
		if(this.allFieldInsts.size() != 0){
			System.out.println("print field collection(prual)");
			System.out.println("============================================");
			for(MyTriple entry : this.allFieldInsts){
				SSAInstruction key = entry.getSSA();
				CGNode value = entry.getCGNode();
				System.out.println("class name" + ":" + value.getMethod().getDeclaringClass().getName().toString());
				System.out.println("method name" + ":" + value.getMethod().getName().toString());
				System.out.println("SSAInstruction" + ":" + key.toString());
				System.out.println("line number" + ":" + IRUtil.getSourceLineNumberFromSSA(value, key));
			}
			System.out.println("============================================");
		}
		System.out.println("finish print");
	}
	//find all loop condition 
	public void doWork(){
		System.out.println("doWork in CollectionInst");
		findAllModifyLoc();		
		SSAInvokeInstruction myInst = isAddOrPut(this.cgNode,this.cgNode.getIR().getControlFlowGraph(),this.inst,this.varType);
		if( myInst!= null){
			LabelledSSA fieldGetTmp = new LabelledSSA(myInst,this.cgNode);
			if(!SSAUtil.isAlreadyLabelled(fieldGetTmp,this.fieldInsts))
				//this.fieldInsts.put(tmp_ssa,tmp_n);
				this.fieldInsts.add(fieldGetTmp);
			if(((SSAInvokeInstruction) myInst).getDeclaredTarget().getName().toString().equals("add") ||
						((SSAInvokeInstruction) myInst).getDeclaredTarget().getName().toString().equals("put")){
				System.out.println("find one via getfield(put/add)" + myInst.toString());

			}else{
				System.out.println("find one via getfield(putAll/addAll)" + myInst.toString());
				SSAInstruction addputAll = SSAUtil.getSSAByDU(this.cgNode, myInst.getUse(1));
				if( addputAll != null){
					MyTriple fieldAllTmp = new MyTriple(addputAll,this.cgNode,-1);
					if(!SSAUtil.isAlreadyInTriple(fieldAllTmp, this.allFieldInsts))
						this.allFieldInsts.add(fieldAllTmp);
				}
			}
				
		}
		printModifyLoc(false);
		if(!this.fieldInsts.isEmpty()){
			for(LabelledSSA entry : this.fieldInsts){
				SSAInstruction key = entry.getSSA();
				CGNode value = entry.getCGNode();
				System.out.println("source" + key.toString());
				this.visited.clear();  //DFS 
				findLoopsInCGN(value,key,null);
				findLoopsInCaller(value,key,0);
			}
		}
		//printResult();
	}
	
	public void doWorkForLocal(){
		System.out.println("doWork in CollectionInst");
		findAllModifyLoc(true);		
		SSAInvokeInstruction myInst = isAddOrPut(this.cgNode,this.cgNode.getIR().getControlFlowGraph(),this.inst,this.varType);
		if( myInst!= null){
			LabelledSSA fieldGetTmp = new LabelledSSA(myInst,this.cgNode);
			if(!SSAUtil.isAlreadyLabelled(fieldGetTmp,this.fieldInsts))
				//this.fieldInsts.put(tmp_ssa,tmp_n);
				this.fieldInsts.add(fieldGetTmp);
			if(((SSAInvokeInstruction) myInst).getDeclaredTarget().getName().toString().equals("add") ||
						((SSAInvokeInstruction) myInst).getDeclaredTarget().getName().toString().equals("put")){
				System.out.println("find one via getfield(put/add)" + myInst.toString());
	
			}else{
				System.out.println("find one via getfield(putAll/addAll)" + myInst.toString());
				SSAInstruction addputAll = SSAUtil.getSSAByDU(this.cgNode, myInst.getUse(1));
				if( addputAll != null){
					MyTriple fieldAllTmp = new MyTriple(addputAll,this.cgNode,-1);
					if(!SSAUtil.isAlreadyInTriple(fieldAllTmp, this.allFieldInsts))
						this.allFieldInsts.add(fieldAllTmp);
				}
			}
				
		}
		
		printModifyLoc(true);
		if(!this.fieldInsts.isEmpty()){
			for(LabelledSSA entry : this.fieldInsts){
				SSAInstruction key = entry.getSSA();
				CGNode value = entry.getCGNode();
				System.out.println("source" + key.toString());
				findLoopsInCGN(value,key,null);
			}
		}
		//printResult();
	}
	
	public void printResult(){
		System.out.println("print the loop condition for all possible callers(including callers''''''callers)");
		if(this.loopCond.size() == 0){
			System.out.println("no result, end...");
			return;
		}
		for(MyTriple entry : this.loopCond){
			SSAInstruction key = entry.getSSA();
			int tmp_int = entry.getParaLoc();
			CGNode value = entry.getCGNode();
			System.out.println("=============================================");
			System.out.println("class name" + ":" + value.getMethod().getDeclaringClass().getName().toString());
			System.out.println("method name" + ":" + value.getMethod().getName().toString());
			System.out.println("SSAInstruction" + ":" + key.toString());
			System.out.println("line number" + ":" + IRUtil.getSourceLineNumberFromSSA(value, key));
			System.out.println("paraLoc" + ":" + tmp_int);
			System.out.println("=============================================");
		}
		System.out.println("end print loop condtions");
		System.out.println("print field collection initialization via putfield");
		if(this.fieldCollectionInit.size() == 0){
			System.out.println("no result, end...");
			return;
		}
		for(MyTriple entry : this.fieldCollectionInit){
			SSAInstruction key = entry.getSSA();
			int tmp_int = entry.getParaLoc();
			CGNode value = entry.getCGNode();
			System.out.println("=============================================");
			System.out.println("class name" + ":" + value.getMethod().getDeclaringClass().getName().toString());
			System.out.println("method name" + ":" + value.getMethod().getName().toString());
			System.out.println("SSAInstruction" + ":" + key.toString());
			System.out.println("line number" + ":" + IRUtil.getSourceLineNumberFromSSA(value, key));
			System.out.println("paraLoc" + ":" + tmp_int);
			System.out.println("=============================================");
		}
		System.out.println("end print field initialization");
	}
}
