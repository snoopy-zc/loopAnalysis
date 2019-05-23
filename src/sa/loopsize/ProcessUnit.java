package sa.loopsize;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ssa.SSAComparisonInstruction;
import com.ibm.wala.ssa.SSAConditionalBranchInstruction;
import com.ibm.wala.ssa.SSAFieldAccessInstruction;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.ssa.SSAPhiInstruction;
import com.ibm.wala.ssa.SymbolTable;

import sa.loop.LoopAnalyzer;
import sa.wala.IRUtil;

public class ProcessUnit {
	//processUnit, it's a unit of processing, it contains CGNode and the SSAInstruction we concern 
	CGNode cgNode;  
	CallGraph cg;
	SSAInstruction inst;
	boolean isLoopCond; //is the loop condition variable we concern  
	boolean isBounded;
	boolean strongBounded;
	boolean specailBounded;
	//after processing a CGNode,  this map stores other CGNode and SSAInstruction we need infer  
	//Map<MyPair,CGNode> newlyCGN; // parameter related 
	HashSet<MyTriple> newlyCGN;
	int level; // how deep from starter 
	// this is for newlyCGN1 to record the parameter location 
	int inIndex = -1;
	LoopAnalyzer looper;
	boolean hasRetSource = false; // for a return case, record its source, if when analyzing the return, find it's related with parameter
    // we can only analyze this point 
	LabelledSSA retSource = null;
	boolean notFindCaller = false;
	//boolean notFindField = false;
	//boolean notFindRet = false;
	LinkedList<String> info;
	HashSet<MyTriple> loop4ArrayLength;
	HashSet<String> relatedConf = new HashSet<String>();
	HashSet<String> constVar = new HashSet<String>();
	boolean isLibraryInvovled = false;
	String txtPath;
	boolean collectionIsBounded = false;
	HashSet<CGNode> opt = new HashSet<CGNode>();
	OptCallChain optObj = null;
	HashSet<MyPair> whoHasRun = new HashSet<MyPair>();
	
	public ProcessUnit(CallGraph cgR, CGNode cgN, SSAInstruction inst, boolean condBranch, int myLevel, int initialIndex, LoopAnalyzer looper, String txtPath,OptCallChain optObj,HashSet<MyPair> whoHasRun) {
		// TODO Auto-generated constructor stub
		this.cg = cgR;
		this.cgNode = cgN;
		this.inst = inst;
		this.isLoopCond = condBranch;
		this.isBounded = true;
		//this.newlyCGN = new HashMap<MyPair,CGNode>();
		this.newlyCGN = new HashSet<MyTriple>();
		this.level = myLevel;
		this.strongBounded = false;
		this.specailBounded = false;
		this.inIndex = initialIndex;
		this.looper = looper;
		this.info = new LinkedList<String>();
		this.loop4ArrayLength = new HashSet<MyTriple>();
		this.txtPath = txtPath;
		this.whoHasRun = whoHasRun;
		System.out.println("new process unit.......................");
		if(optObj != null){
			this.optObj = optObj;
			SimpleSlicing sb = new SimpleSlicing(optObj.inst,optObj.cgN,optObj.cg,this.whoHasRun);
			sb.getAllNaiveSlicing(optObj.inst,optObj.cgN,optObj.cg);
			//System.out.println(optObj.cgN.getMethod().getName().toString());
			//System.out.println(optObj.inst.toString());
			//System.out.println(sb.slicerNode.size());
			//for(CGNode x : sb.slicerNode){
			//	System.out.println(x.getMethod().toString());
			//}
			//assert 0 == 1;
			this.opt.addAll(sb.slicerNode);
			//System.out.println("sbbbbbbbbbbbbbbbbbbbxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");
		}
	}
	
	public void setRetSource(boolean hasRetSource, LabelledSSA retSource){
		this.hasRetSource = hasRetSource;
		this.retSource = retSource;
	}
	
	public void run(){
		int numOfUse = this.inst.getNumberOfUses();
		SSAInstruction tmp = null;
		if(this.isLoopCond){
			assert (this.inst instanceof SSAConditionalBranchInstruction);
			assert numOfUse == 2;
			//1. collection case TODO
			//2. the second parameter is constant or parameter 
			//3. normal case 
			int varNum = this.inst.getUse(1);
			int firstVarNum = this.inst.getUse(0);
			SSAInstruction firstVar = SSAUtil.getSSAByDU(this.cgNode,firstVarNum); // for the case like set.size() < 2
			//System.out.println(varNum);
			//System.out.println(this.cgNode.toString());
			System.out.println("Processour unit run:" + this.inst.toString());
			HashSet<MyTriple> moreRet = new HashSet<MyTriple>(); // to record the user defined iterator result 
			tmp = SSAUtil.isCollection(this.cgNode,this.inst,this.cg,this.looper,moreRet,this.txtPath,this.whoHasRun);
			if(tmp == null && moreRet.size() != 0){
				System.out.println("user defined iterator");
				SSAUtil.putNewlyCGN(this.newlyCGN,moreRet);
				return;
			}
			if(tmp != null){ // collection 
				
				// user defined bounded
				System.out.println("xxxxxxxxxxcollectionxxxxxxxxxxxx" + tmp.toString());
				//assert 0 == 1;
				if(tmp instanceof SSAInvokeInstruction){ //handle special case, 1. user defined bounded 2. speical iterator
					if(SSAUtil.userDefinedBounded(tmp, this.cgNode,this.relatedConf)){
						this.strongBounded = true;
						return;
					}
					if(((SSAInvokeInstruction) tmp).getDeclaredTarget().getDeclaringClass().getName().toString().equals("Lorg/apache/hadoop/hdfs/server/namenode/DatanodeDescriptor$BlockIterator") &&
							((SSAInvokeInstruction) tmp).getDeclaredTarget().getName().toString().equals("<init>")){
						ArrayList<LabelledSSA> retSSAs = (ArrayList<LabelledSSA>) SSAUtil.getSSAVariable("org.apache.hadoop.hdfs.server.namenode.BlocksMap$BlockInfo-190",this.cg);						
						//assert 0 == 1;
						assert retSSAs.size() == 1;
						LabelledSSA my_tmp = null;
						for(LabelledSSA a : retSSAs){
						//	System.out.println("SBBBBBBBBBBB" + a.getSSA());
							//if(a.getCGNode().getMethod().getDeclaringClass().getName().toString().equals("findStorageInfo")){
								my_tmp = a;
							//}
						}
						
						MyTriple specialIterator = new MyTriple(my_tmp.getSSA(),my_tmp.getCGNode(),-1);
						//assert 0 == 1;
						//specialIterator.setHasChild(false);
						if(!SSAUtil.isAlreadyInTriple(specialIterator,this.newlyCGN))
							//if(OptCallChain.isValidSlicing(this.opt,this.cgNode))
								this.newlyCGN.add(specialIterator);
						return;
					}
					if(((SSAInvokeInstruction) tmp).getDeclaredTarget().getDeclaringClass().getName().toString().equals("Lorg/apache/hadoop/hdfs/server/blockmanagement/DatanodeDescriptor$BlockIterator") &&
							((SSAInvokeInstruction) tmp).getDeclaredTarget().getName().toString().equals("<init>")){
						MyTriple specialIterator = new MyTriple(tmp,this.cgNode,-3);
						specialIterator.setHasChild(false);
						if(!SSAUtil.isAlreadyInTriple(specialIterator,this.newlyCGN))
							//if(OptCallChain.isValidSlicing(this.opt,this.cgNode))
								this.newlyCGN.add(specialIterator);
						return;
					}
					
					if(((SSAInvokeInstruction) tmp).getDeclaredTarget().getDeclaringClass().getName().toString().equals("Lorg/apache/hadoop/hdfs/server/blockmanagement/DatanodeStorageInfo$BlockIterator") &&
							((SSAInvokeInstruction) tmp).getDeclaredTarget().getName().toString().equals("<init>")){
						ArrayList<LabelledSSA> retSSAs = (ArrayList<LabelledSSA>) SSAUtil.getSSAVariable("org.apache.hadoop.hdfs.server.blockmanagement.BlockInfo.findStorageInfo-282",this.cg);						
						//assert 0 == 1;
						assert retSSAs.size() == 1;
						LabelledSSA my_tmp = null;
						for(LabelledSSA a : retSSAs){
						//	System.out.println("SBBBBBBBBBBB" + a.getSSA());
							//if(a.getCGNode().getMethod().getDeclaringClass().getName().toString().equals("findStorageInfo")){
								my_tmp = a;
							//}
						}
						
						MyTriple specialIterator = new MyTriple(my_tmp.getSSA(),my_tmp.getCGNode(),-1);
						//MyTriple specialIterator = new MyTriple(tmp,this.cgNode,-3);
						//specialIterator.setHasChild(false);
						if(!SSAUtil.isAlreadyInTriple(specialIterator,this.newlyCGN))
							//if(OptCallChain.isValidSlicing(this.opt,this.cgNode))
								this.newlyCGN.add(specialIterator);
						return;
					}
					
					if(((SSAInvokeInstruction) tmp).getDeclaredTarget().getDeclaringClass().getName().toString().equals("Lorg/apache/hadoop/hdfs/protocol/BlockListAsLongs") &&
							((SSAInvokeInstruction) tmp).getDeclaredTarget().getName().toString().equals("getBlockReportIterator")){
						ArrayList<LabelledSSA> retSSAs = (ArrayList<LabelledSSA>) SSAUtil.getSSAVariable("org.apache.hadoop.hdfs.server.datanode.fsdataset.impl.ReplicaMap.add-115",this.cg);
						LabelledSSA my_tmp = null;
						for(LabelledSSA a : retSSAs){
						    //System.out.println("SBBBBBBBBBBB" + a.getSSA());
							my_tmp = a;
						}	
						assert retSSAs.size() == 2;
						//MyTriple specialIterator = new MyTriple(my_tmp.getSSA(),my_tmp.getCGNode(),-1);
						//assert 0 == 1;
						//specialIterator.setHasChild(false);
						MyTriple specialIterator = new MyTriple(my_tmp.getSSA(),my_tmp.getCGNode(),-1);
						//specialIterator.setHasChild(false);
						if(!SSAUtil.isAlreadyInTriple(specialIterator,this.newlyCGN))
							//if(OptCallChain.isValidSlicing(this.opt,this.cgNode))
								this.newlyCGN.add(specialIterator);
						return;
					}
				}
				
				if(tmp instanceof SSANewInstruction){
					System.out.println("this loop variable relies on a local collection variable");
					System.out.println(this.inst.toString());
					System.out.println(tmp.toString());
					
					//user defined bounded
					/*if(((SSANewInstruction) tmp).getNewSite().getDeclaredType().getName().toString().equals("Lorg/apache/hadoop/conf/Configuration")){
						assert 0 == 1;
						this.specailBounded = true;
						return;
					}*/
					
					CollectionInst collObj = new CollectionInst(this.cg,this.cgNode,tmp.getDef(0),this.looper,this.txtPath,this.whoHasRun);
					collObj.doWorkForLocal();
					if(collObj.loopCond.size() == 0 && collObj.allFieldInsts.size() == 0){
						//this.isBounded &= true;
						this.strongBounded = true;
					}
					else{
						//this.newlyCGN.putAll(collObj.loopCond);
						if(collObj.loopCond.size() != 0)
							SSAUtil.putNewlyCGN(this.newlyCGN, collObj.loopCond);
						if(collObj.allFieldInsts.size() != 0)
							SSAUtil.putNewlyCGN(this.newlyCGN, collObj.allFieldInsts);
					}
					
					System.out.println(tmp.toString());
					CollectionViaPara paraCollObj = new CollectionViaPara(tmp.getDef(0),this.cg,this.cgNode,this.looper,this.txtPath,this.whoHasRun);
					paraCollObj.doWork();
					if(paraCollObj.retLoops.size() != 0 )
						SSAUtil.putNewlyCGN(this.newlyCGN,paraCollObj.retLoops);
						
					
				}else if(tmp instanceof SSAGetInstruction){
					System.out.println("this loop variable relies on a field collection variable");
					System.out.println(this.inst.toString());
					System.out.println(tmp.toString());
					String varName = ((SSAFieldAccessInstruction)tmp).getDeclaredField().getName().toString();
					String varType = ((SSAFieldAccessInstruction)tmp).getDeclaredFieldType().getName().toString();
					String className = ((SSAFieldAccessInstruction)tmp).getDeclaredField().getDeclaringClass().getName().toString();
					CollectionInst collObj = new CollectionInst(this.cg,this.cgNode,tmp,varName,varType,className,this.looper,this.txtPath,this.whoHasRun);
					collObj.doWork();
					//collObj.printResult();
					if(collObj.loopCond.size() == 0 && collObj.fieldCollectionInit.size() == 0 && collObj.allFieldInsts.size() == 0){
						//this.isBounded &= true;
						this.strongBounded = true;
					}
					else{
						//this.newlyCGN.putAll(collObj.loopCond);
						if(collObj.loopCond.size() != 0)
							SSAUtil.putNewlyCGN(this.newlyCGN,collObj.loopCond);
						if(collObj.fieldCollectionInit.size() != 0)
							SSAUtil.putNewlyCGN(this.newlyCGN, collObj.fieldCollectionInit);
						if(collObj.allFieldInsts.size() != 0)
							SSAUtil.putNewlyCGN(this.newlyCGN, collObj.allFieldInsts);
					}
				/*}else if(tmp.getNumberOfUses() != 0 && SSAUtil.isVnParameter(this.cgNode,tmp.getUse(0))){
					System.out.println("this loop variable relies on parameter collection");
					System.out.println(tmp.toString());
					if(SSAUtil.isSpecialCollection4CA("null",this.cgNode.getMethod().getDeclaringClass().getName().toString())){
						System.out.println("is the library we model");
						MyTriple specialIterator = new MyTriple(tmp,this.cgNode,-3);
						specialIterator.setHasChild(false);
						if(!SSAUtil.isAlreadyInTriple(specialIterator,this.newlyCGN))
							//if(OptCallChain.isValidSlicing(this.opt,this.cgNode))
								this.newlyCGN.add(specialIterator);
						return;
					}
					int paraLoc = SSAUtil.getParameterLoc(tmp.getUse(0),this.cgNode);
					assert paraLoc >= 1;
					ParameterInst paraObj = new ParameterInst(paraLoc,this.cg,this.cgNode);
					if(!this.hasRetSource)
						paraObj.getPossibleCaller();
					paraObj.printCalleeLoc();
					if(paraObj.calleeLoc.size() == 0){
						//this.isBounded &= true;
						this.strongBounded = true;
					}
					else{
						for(LabelledSSA para_entry : paraObj.calleeLoc){
							if(this.hasRetSource){
								if(!para_entry.getCGNode().toString().equals(this.retSource.getCGNode().toString()) || !para_entry.getSSA().toString().equals(this.retSource.getSSA().toString()))
									continue;
							}
							SSAInstruction para_key = para_entry.getSSA();
							CGNode para_value = para_entry.getCGNode();
							int myLoc;
							if(((SSAInvokeInstruction)para_key).isStatic()){
								myLoc = paraLoc-1; 
							}else{
								myLoc = paraLoc; //if not static, the first argument should be a class variable 
							}
							//MyPair tmp_pair = new MyPair(para_key,myLoc);
							MyTriple tmp_triple = new MyTriple(para_key,para_value,myLoc);
							//this.newlyCGN.put(tmp_pair, para_value);
							if(!SSAUtil.isAlreadyInTriple(tmp_triple,this.newlyCGN))
								if(OptCallChain.isValidSlicing(this.opt,para_value))
									this.newlyCGN.add(tmp_triple);
						}
					}*/
				}else{
					System.out.println("corner case 1 in process unit");
					this.isBounded &= normalCaseProcess(tmp);
				}
			}else if(firstVar!= null && SSAUtil.isCollectionSizeMethod(firstVar)){
				System.out.println("this loop variable relies on collection's size method");
				
				this.isBounded &= normalCaseProcess(firstVar);				
			}else if(SSAUtil.isVnParameter(this.cgNode,varNum)){ //(not collection) it's parameter 
								
				System.out.println("this loop variable relies on parameter");
				System.out.println(this.inst.toString());
				int paraLoc = SSAUtil.getParameterLoc(varNum,this.cgNode);
				assert paraLoc >= 1;
				ParameterInst paraObj = new ParameterInst(paraLoc,this.cg,this.cgNode,this.whoHasRun);
				paraObj.getPossibleCaller();
				if(!this.hasRetSource)
					paraObj.printCalleeLoc();
				if(paraObj.calleeLoc.size() == 0){
					//this.isBounded &= true;
					this.strongBounded = true;
				}
				else{
					for(LabelledSSA para_entry : paraObj.calleeLoc){
						if(this.hasRetSource){
							if(!para_entry.getCGNode().toString().equals(this.retSource.getCGNode().toString()) || !para_entry.getSSA().toString().equals(this.retSource.getSSA().toString()))
								continue;
						}
						SSAInstruction para_key = para_entry.getSSA();
						int myLoc;
						CGNode para_value = para_entry.getCGNode();
						if(((SSAInvokeInstruction)para_key).isStatic()){
							myLoc = paraLoc-1;
						}else{
							myLoc = paraLoc;
						}
						MyTriple tmp_triple = new MyTriple(para_key,para_value,myLoc);
						//this.newlyCGN.put(tmp_pair, para_value);
						if(!SSAUtil.isAlreadyInTriple(tmp_triple,this.newlyCGN))
							if(OptCallChain.isValidSlicing(this.opt,para_value))
								this.newlyCGN.add(tmp_triple);
					}
				}
			/*}else if(((tmp = SSAUtil.getSSAByDU(this.cgNode, this.inst.getUse(1))) != null) && (tmp instanceof SSAInvokeInstruction) && 
					((SSAInvokeInstruction) tmp).getDeclaredTarget().getDeclaringClass().getName().toString().startsWith("Ljava/lang/Math")&&
							(((SSAInvokeInstruction) tmp).getDeclaredTarget().getName().toString().equals("min") &&
							((SSAInvokeInstruction) tmp).isStatic())){
					this.isBounded &= normalCaseProcess(tmp);*/
				
	
			}else if((tmp = SSAUtil.isArrayLengthInst(varNum,this.cgNode)) != null){
				System.out.println("array length related");
				this.isBounded &= normalCaseProcess(tmp);
			//}else if((tmp = SSAUtil.getSSAIndexByDefvn(this.cgNode.getIR().getInstructions(),varNum))!=null){
			
			}else if(SSAUtil.isConstant(this.cgNode,varNum)){
	//			this.constVar.add(SSAUtil.getConstant(this.cgNode, varNum));
				System.out.println(this.inst.toString());
				System.out.println("this loop may be bouneded by constant variable" + SSAUtil.getConstant(this.cgNode, varNum));
				if(firstVar != null && (firstVar instanceof SSAComparisonInstruction)){
					System.out.println("this loop relies on an expression");
					MyTriple specialCond = new MyTriple(this.inst,this.cgNode,-5);
					specialCond.setHasChild(false);
					if(!SSAUtil.isAlreadyInTriple(specialCond,this.newlyCGN))
						//if(OptCallChain.isValidSlicing(this.opt,this.cgNode))
							this.newlyCGN.add(specialCond);
				}else if(firstVar != null && (firstVar instanceof SSAPhiInstruction)){
					System.out.println("the value for this condition variable changes in loop");
					if(SSAUtil.isSpecialLoop(this.inst,this.cgNode))
						this.isBounded &= normalCaseProcess(firstVar);
					else{
						System.out.println("this is not the special loop(a++,a--)");
						MyTriple specialLoop = new MyTriple(this.inst,this.cgNode,-6);
						specialLoop.setHasChild(false);
						if(!SSAUtil.isAlreadyInTriple(specialLoop,this.newlyCGN))
							//if(OptCallChain.isValidSlicing(this.opt,this.cgNode))
								this.newlyCGN.add(specialLoop);
						return;
					}
				}else if(firstVar != null && firstVar instanceof SSAInvokeInstruction){
					System.out.println("this loop relies on a function call");
					this.isBounded &= normalCaseProcess(firstVar);
				}else if(firstVar != null && firstVar instanceof SSAGetInstruction){
					//this.isBounded &= normalCaseProcess(firstVar);
					if(((SSAGetInstruction) firstVar).getDeclaredFieldType().getName().toString().equals("Z")){
						System.out.println("while(boolean) case");
						MyTriple tmp_triple = new MyTriple(this.inst,this.cgNode,-7);
						tmp_triple.setHasChild(false);
						//this.newlyCGN.put(tmp_pair, para_value);
						if(!SSAUtil.isAlreadyInTriple(tmp_triple,this.newlyCGN))
							//if(OptCallChain.isValidSlicing(this.opt,this.cgNode))
								this.newlyCGN.add(tmp_triple);
						return;
					}
					System.out.println("this loop relies on a field var");

					FieldInst fieldObj = new FieldInst(firstVar,this.cg,this.cgNode);
					fieldObj.getAllModifyLoc();
					fieldObj.printAllModifyLoc();
					if(fieldObj.modifyLoc.size() != 0){
						//this.notFindField = false;
						for(LabelledSSA field_entry : fieldObj.modifyLoc){
							SSAInstruction field_key = field_entry.getSSA();
							CGNode field_value = field_entry.getCGNode();
							MyTriple tmp_triple = new MyTriple(field_key,field_value,-1);
							//this.newlyCGN.put(tmp_pair, para_value);
							if(!SSAUtil.isAlreadyInTriple(tmp_triple,this.newlyCGN))
								//if(OptCallChain.isValidSlicing(this.opt,field_value))
									this.newlyCGN.add(tmp_triple);
						}
					}					
					
				}else{
					System.out.println("this loop is bouned by constant variable" + SSAUtil.getConstant(this.cgNode, varNum));
					SSAUtil.markConstant(this.cgNode, this.inst, 1 , this.constVar, false);	
					//this.constVar.add(SSAUtil.getConstant(this.cgNode,varNum));
					this.isBounded &= true;
					this.strongBounded = true;
				}
			//}else if((tmp = SSAUtil.isArrayLengthInst(varNum,this.cgNode.getIR().getInstructions()))!=null){
			}else if((tmp = SSAUtil.getSSAByDU(this.cgNode, varNum)) != null){
				System.out.println("normal case for branch condition");
				this.isBounded &= normalCaseProcess(tmp);
			}else{
				System.out.println("corner case 2 in process unit"); 
				this.isBounded &= true;
			}
		}else{
			System.out.println("not a branch, normal case");
			System.out.println(this.inst.toString());
			this.isBounded &= normalCaseProcess(this.inst);
		}
	}
	
	public boolean normalCaseProcess(SSAInstruction inst){
		boolean retVal = false;
		boolean collectionBounded = false;
		if(this.level > 10000){
			 System.out.println("exceed the threshold");
			 return false;
		}
		//System.out.println(inst.toString());
		//System.out.println(inst.toString());
		//System.out.println(this.inIndex);
		
		if(this.cgNode.getMethod().getDeclaringClass().getName().toString().equals("Lorg/apache/cassandra/cql3/QueryProcessor")){
			//assert 0 == 1;
			MyTriple specialIterator = new MyTriple(inst,this.cgNode,-8);
			specialIterator.setHasChild(false);
			if(!SSAUtil.isAlreadyInTriple(specialIterator,this.newlyCGN))
				//if(OptCallChain.isValidSlicing(this.opt,this.cgNode))
					this.newlyCGN.add(specialIterator);
			return true;
		}
		
		
		//System.out.println("sbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb" + inst.toString());
		if(inst instanceof SSAInvokeInstruction){
			if(((SSAInvokeInstruction)inst).getDeclaredTarget().getDeclaringClass().getName().toString().equals("Lorg/apache/cassandra/io/sstable/SSTableScanner") &&
			  ((SSAInvokeInstruction)inst).getDeclaredTarget().getName().toString().equals("hasNext")){
				ArrayList<LabelledSSA> retSSAs = (ArrayList<LabelledSSA>) SSAUtil.getSSAVariable("org.apache.cassandra.io.sstable.SSTableScanner.<init>-54",this.cg);
				LabelledSSA my_tmp = null;
				for(LabelledSSA a : retSSAs){
				    System.out.println("SBBBBBBBBBBB" + a.getSSA());
				    if(a.getSSA() instanceof SSAInvokeInstruction)
				    	my_tmp = a;
				}	
				//assert 0 == 1;
				//assert retSSAs.size() == 2;
				//MyTriple specialIterator = new MyTriple(my_tmp.getSSA(),my_tmp.getCGNode(),-1);
				//assert 0 == 1;
				//specialIterator.setHasChild(false);
				MyTriple specialIterator = new MyTriple(my_tmp.getSSA(),my_tmp.getCGNode(),-1);
				//specialIterator.setHasChild(false);
				if(!SSAUtil.isAlreadyInTriple(specialIterator,this.newlyCGN))
					//if(OptCallChain.isValidSlicing(this.opt,this.cgNode))
						this.newlyCGN.add(specialIterator);
	
				return true;
			}
		}
		
		BackwardSlicing bws = new BackwardSlicing(inst,this.cgNode,this.inIndex,this.looper,this.cg,this.txtPath,this.whoHasRun);
		bws.getDataSlicing(inst);
		if(bws.info.size() != 0){
			this.info.addAll(bws.info);
		}
		if(bws.relatedConf.size() != 0)
			this.relatedConf.addAll(bws.relatedConf);
		if(bws.constVar.size() != 0)
			this.constVar.addAll(bws.constVar);
		if(bws.isLibBounded)
			this.isLibraryInvovled = true;
		bws.printSlice();
				
		if((bws.localCollection.size() == 0 || noNeedAnalyzeLocalC(bws.localCollection)) && 
				(bws.fieldCollection.size() == 0 || noNeedAnalyzeFieldC(bws.fieldCollection))){
			collectionBounded = true;
			System.out.println("no need analyze collection" + ":" + this.cgNode.getMethod().getSignature().toString());
		}
		
		if(bws.isEmptyColl)
			this.collectionIsBounded = true;
		if(bws.isBoundedCondInThisCFG()){
			retVal = true;
			if(bws.isUserBounded)  // user defined bounded
				this.specailBounded = true;
			if(collectionBounded){
				this.strongBounded = true;
				this.collectionIsBounded = true;
			}
			if(bws.hasArray)
				this.collectionIsBounded = true;
			
			if(this.specailBounded || this.specailBounded){
				System.out.println("it's bounded in " + ":" + this.cgNode.getMethod().getSignature().toString());
				System.out.println("***********************************************");
				System.out.println("class name" + ":" + this.cgNode.getMethod().getDeclaringClass().getName().toString());
				System.out.println("method name" + ":" + this.cgNode.getMethod().getName().toString());
				System.out.println("SSAInstruction" + ":" + inst.toString());
				System.out.println("line number" + ":" + IRUtil.getSourceLineNumberFromSSA(this.cgNode,inst));
				System.out.println("level" + ":" + this.level);
				System.out.println("***********************************************");
			}
		}else{
			if(bws.callRet.size() != 0){
				retVal = true;
				System.out.println("normal case : call return");
				System.out.println(this.inst.toString());
				for(SSAInstruction ssa_tmp : bws.callRet){

					//assert 1==0;*/
					RetInst retObj = new RetInst(ssa_tmp,this.cgNode,this.cg);
					retObj.getCalleeRetInst();
					retObj.pruneRetInsts();
					if(retObj.retDetail.size() == 0)
						System.out.println("no need to analyze call return");
					if(retObj.retDetail.size()!=0){
						//this.notFindRet = false;
						for(LabelledSSA ret_entry : retObj.retDetail){
							SSAInstruction ret_key = ret_entry.getSSA();
							CGNode ret_value = ret_entry.getCGNode();
							//System.out.println("wtfssssss...." + ret_key.toString());
							MyTriple tmp_triple = new MyTriple(ret_key,ret_value,-1);
							//this.opt.clear();
							tmp_triple.setOptChain(ssa_tmp,this.cgNode, this.cg);
							//System.out.println("wtfsssssss.......2" + ssa_tmp.toString());
							//System.out.println(SSAUtil.getRealCallSiteFromSlicing(bws.slicer,retObj.calledMeth,retObj.calledClass,, returnType));
						
							
							tmp_triple.setRetCallLoc(this.cgNode, ssa_tmp);
							//this.newlyCGN.put(tmp_pair, para_value);
							if(!SSAUtil.isAlreadyInTriple(tmp_triple,this.newlyCGN))
								//if(OptCallChain.isValidSlicing(this.opt,this.cgNode))
									this.newlyCGN.add(tmp_triple);
						}
					}
				}
			}
			if(bws.paraList.size() != 0){
				int paraNum = 0;
				retVal = true;
				System.out.println("normal case : parameter");
				for(Integer loc_tmp : bws.paraList){
					ParameterInst paraObj = new ParameterInst(loc_tmp.intValue(),this.cg,this.cgNode,this.whoHasRun);
					paraObj.getPossibleCaller();
					if(!this.hasRetSource)
						paraObj.printCalleeLoc();
					if(paraObj.calleeLoc.size()!=0){
						paraNum += paraObj.calleeLoc.size();
						for(LabelledSSA para_entry : paraObj.calleeLoc){
							if(this.hasRetSource == true){
								//assert 0 == 1;
								/*System.out.println(para_entry.getSSA().toString());
								System.out.println(this.retSource.getSSA().toString());
								System.out.println(para_entry.getSSA().toString().equals(this.retSource.getSSA().toString()));
								System.out.println(para_entry.getCGNode().toString().equals(this.retSource.getCGNode().toString()));
								System.out.println("sbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");*/
								if(!para_entry.getCGNode().toString().equals(this.retSource.getCGNode().toString()) || !para_entry.getSSA().toString().equals(this.retSource.getSSA().toString()))
									continue;
							}
							SSAInstruction para_key = para_entry.getSSA();
							CGNode para_value = para_entry.getCGNode();
							//System.out.println("xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" + loc_tmp);
							//MyPair tmp_pair = new MyPair(para_key,SSAUtil.getParameterLoc(loc_tmp.intValue(),this.cgNode));
							int myLoc;
							if(((SSAInvokeInstruction)para_key).isStatic()){
								myLoc = loc_tmp-1;
							}else{
								myLoc = loc_tmp;
							}	
							MyTriple tmp_triple = new MyTriple(para_key,para_value,myLoc);
							//this.newlyCGN.put(tmp_pair, para_value);
							if(!SSAUtil.isAlreadyInTriple(tmp_triple,this.newlyCGN))
								if(OptCallChain.isValidSlicing(this.opt,para_value))
									this.newlyCGN.add(tmp_triple);
						}
					}
				}
				if(paraNum == 0)
					this.notFindCaller = true;
			}
			if(bws.thisList.size() != 0){
				retVal = true;
				System.out.println("normal case : field");
				for(SSAInstruction fieldAcc : bws.thisList){
					FieldInst fieldObj = new FieldInst(fieldAcc,this.cg,this.cgNode);
					fieldObj.getAllModifyLoc();
					fieldObj.printAllModifyLoc();
					if(fieldObj.modifyLoc.size() != 0){
						//this.notFindField = false;
						for(LabelledSSA field_entry : fieldObj.modifyLoc){
							SSAInstruction field_key = field_entry.getSSA();
							CGNode field_value = field_entry.getCGNode();
							MyTriple tmp_triple = new MyTriple(field_key,field_value,-1);
							//this.newlyCGN.put(tmp_pair, para_value);
							if(!SSAUtil.isAlreadyInTriple(tmp_triple,this.newlyCGN))
								//if(OptCallChain.isValidSlicing(this.opt,field_value))
									this.newlyCGN.add(tmp_triple);
						}
					}					
				}
			}
			if(bws.loop4ArrayLength.size() != 0){
				retVal = true;
				System.out.println("normal case: loop for array length");
				for(MyTriple loopAcc : bws.loop4ArrayLength){
					if(!SSAUtil.isAlreadyInTriple(loopAcc,this.newlyCGN))
						//if(OptCallChain.isValidSlicing(this.opt,loopAcc.getCGNode()))
							this.newlyCGN.add(loopAcc);		
				}
			}
			if(bws.collectionInCallee.size() != 0){
				retVal = true;
				System.out.println("normal case: collection in callee");
				for(MyTriple collInCallee : bws.collectionInCallee){
					if(!SSAUtil.isAlreadyInTriple(collInCallee,this.newlyCGN))
						//if(OptCallChain.isValidSlicing(this.opt,collInCallee.getCGNode()))
							this.newlyCGN.add(collInCallee);
				}
			}
			
			if(bws.binaryOpLoop.size() != 0){
				retVal = true;
				System.out.println("normal case: loop for binary operation:add");
				for(MyTriple binLoop : bws.binaryOpLoop){
					if(!SSAUtil.isAlreadyInTriple(binLoop, this.newlyCGN))
						//if(OptCallChain.isValidSlicing(this.opt,binLoop.getCGNode()))
							this.newlyCGN.add(binLoop);
				}
			}
			
			if(bws.localCollection.size() != 0){
				retVal = true;
				System.out.println("normal case: local collection");
				// do nothing, already analyze them in noNeedAnalyzeLocalC function 
			}
			if(bws.fieldCollection.size() != 0){
				retVal = true;
				System.out.println("normal case: field collection");
				// do nothing, already analyze them in noNeedAnalyzeFieldC function
			}
		}
		if(this.notFindCaller)
			System.out.println("for some reasons, we can't find caller, maybe RPC");
		//if(this.notFindCaller || this.notFindField || this.notFindRet){
			//System.out.println("for some reasons, we can't find caller, filed modification, collection modification, function returns");
		    //this.specailBounded = true;
		//}
		return retVal;
	}
	
	public boolean noNeedAnalyzeFieldC(HashSet<SSAInstruction> fieldCs){
		int size = 0;
		for(SSAInstruction fieldC : fieldCs){
			String varName = ((SSAFieldAccessInstruction)fieldC).getDeclaredField().getName().toString();
			String varType = ((SSAFieldAccessInstruction)fieldC).getDeclaredFieldType().getName().toString();
			String varInClass = ((SSAFieldAccessInstruction)fieldC).getDeclaredField().getDeclaringClass().getName().toString();
			System.out.println(varName);
			System.out.println(varInClass);
			/*if(SSAUtil.isSpecialCollection4CA(varName,varInClass)){
				MyTriple specialIterator = new MyTriple(fieldC,this.cgNode,-3);
				specialIterator.setHasChild(false);
				if(!SSAUtil.isAlreadyInTriple(specialIterator,this.newlyCGN))
					if(OptCallChain.isValidSlicing(this.opt,this.cgNode))
						this.newlyCGN.add(specialIterator);
				continue;
			}*/
			CollectionInst collObj = new CollectionInst(this.cg,this.cgNode,fieldC,varName,varType,varInClass,this.looper,this.txtPath,this.whoHasRun);
			collObj.doWork();
			collObj.printResult();
			size += collObj.loopCond.size();
			size += collObj.fieldCollectionInit.size();
			size += collObj.allFieldInsts.size();
			if(collObj.loopCond.size() == 0 && collObj.fieldCollectionInit.size() == 0 && collObj.allFieldInsts.size() == 0){
				//this.isBounded &= true;
			}
			else{
				//this.newlyCGN.putAll(collObj.loopCond);
				if(collObj.loopCond.size() != 0)
					SSAUtil.putNewlyCGN(this.newlyCGN,collObj.loopCond);
				if(collObj.fieldCollectionInit.size() != 0)
					SSAUtil.putNewlyCGN(this.newlyCGN,collObj.fieldCollectionInit);
				if(collObj.allFieldInsts.size() != 0)
					SSAUtil.putNewlyCGN(this.newlyCGN,collObj.allFieldInsts);
			}
		}
		if(size == 0)
			return true;
		else
			return false;
	}
	
	public boolean noNeedAnalyzeLocalC(HashSet<SSAInstruction> localCs){
		int size = 0;
		for(SSAInstruction localC : localCs){
			CollectionInst collObj;
			if(localC instanceof SSANewInstruction)
				collObj = new CollectionInst(this.cg,this.cgNode,localC.getDef(0),this.looper,this.txtPath,this.whoHasRun);
			else if(localC.getNumberOfUses() > 0)
				collObj = new CollectionInst(this.cg,this.cgNode,localC.getUse(0),this.looper, this.txtPath,this.whoHasRun);
			else 
				collObj = new CollectionInst(this.cg,this.cgNode,localC.getDef(0),this.looper,this.txtPath,this.whoHasRun);
			collObj.doWorkForLocal();
			size += collObj.loopCond.size();
			size += collObj.allFieldInsts.size();
			//this.newlyCGN.putAll(collObj.loopCond);
			if(collObj.loopCond.size()!=0)
				SSAUtil.putNewlyCGN(this.newlyCGN,collObj.loopCond);
			if(collObj.allFieldInsts.size()!=0)
				SSAUtil.putNewlyCGN(this.newlyCGN,collObj.allFieldInsts);
		}
		if(size == 0)
			return true;
		else
			return false;
	}
	
	public void printNewlyCGN(){
		System.out.println("print newly added CGNode");
		if(this.newlyCGN.size() == 0){
			if(this.specailBounded || this.strongBounded)
				System.out.println("no result because of being bounded, end ...");
			return;
		}
		for(MyTriple entry : this.newlyCGN){
			SSAInstruction key = entry.getSSA();
			int tmp_int = entry.getParaLoc();
			CGNode value = entry.getCGNode();
			System.out.println("=============================================");
			System.out.println("class name" + ":" + value.getMethod().getDeclaringClass().getName().toString());
			System.out.println("method name" + ":" + value.getMethod().getName().toString());
			System.out.println("SSAInstruction" + ":" + key.toString());
			System.out.println("paraLoc" + ":" + tmp_int);
			System.out.println("line number" + ":" + IRUtil.getSourceLineNumberFromSSA(value,key));
			System.out.println("level" + ":" + this.level);
			System.out.println("=============================================");
		}
		System.out.println("finish print newly added CGNode");
	}
	
}
