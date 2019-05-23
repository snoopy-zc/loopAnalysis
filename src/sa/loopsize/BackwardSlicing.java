package sa.loopsize;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.shrikeBT.IBinaryOpInstruction;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSABinaryOpInstruction;
import com.ibm.wala.ssa.SSACFG;
import com.ibm.wala.ssa.SSAConditionalBranchInstruction;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.ssa.SSAPhiInstruction;
import com.ibm.wala.ssa.SSAPutInstruction;
import com.ibm.wala.ssa.SymbolTable;
import com.ibm.wala.util.graph.dominators.Dominators;
import com.text.Checker;

import sa.loop.LoopAnalyzer;
import sa.loop.LoopInfo;
import sa.wala.IRUtil;

public class BackwardSlicing {
	public SSAInstruction ssaInst;
	public CGNode cgNode;
	public SSAInstruction[] allInsts;
	public IR ir;
	// they are all in same CGNode, so they can't be same 
	public HashSet<SSAInstruction> outerInst; // outer instruction, which doesn't have backward slicing 
	public HashSet<SSAInstruction> specialOuter; // for modeled function call 
	public HashSet<SSAInstruction> slicer;//slicing result
	public HashSet<SSAInstruction> callRet; //function call instruction 
	public HashSet<Integer> paraList; //we need analyze parameter 
	public HashSet<SSAInstruction> thisList; // we need analyze class field 
	public HashSet<SSAInstruction> localCollection; //we need analyze local collection 
	public HashSet<SSAInstruction> fieldCollection; // we need analyze field collection 
	public boolean inStatic = false;
	public int paraIndex = -1; // for all uses, which one is paraIndex
	public HashSet<SSAInstruction> recordCaller; //actually para_index, we can only use once 
							 //because foo1(foo2), for foo2 we can't use the same para_index like foo1
	public boolean isUserBounded = false;
	public boolean isLibBounded = false;
	public boolean hasArray = false;
	public boolean isEmptyColl = false;
	public LinkedList<String> info;  //additional information
	public HashSet<String> relatedConf = new HashSet<String>();
	public HashSet<String> constVar = new HashSet<String>();
	public LoopAnalyzer looper;
	public HashSet<MyTriple> loop4ArrayLength;
	public HashSet<MyTriple> collectionInCallee;
	public HashSet<MyTriple> binaryOpLoop; // like v24 = binaryop(add) v20 , v9:#1, we need analyze the loop

	public CallGraph cg;
	public SSAInstruction starter;
	public int exactPara;
	public String txtPath;
	public HashSet<MyPair>whoHasRun;
	//OptCallChain opt = null;
	
	public BackwardSlicing(SSAInstruction inst, CGNode cgN, int para_index, LoopAnalyzer looper, CallGraph cg, String txtPath,HashSet<MyPair>whoHasRun){
		this.ssaInst = inst;
		this.cgNode = cgN;
		this.outerInst = new HashSet<SSAInstruction>();
		this.slicer = new HashSet<SSAInstruction>();
		this.callRet = new HashSet<SSAInstruction>();
		this.paraList = new HashSet<Integer>();
		this.thisList = new HashSet<SSAInstruction>();
		this.allInsts = cgN.getIR().getInstructions();
		this.ir = cgN.getIR();
		this.inStatic = cgN.getMethod().isStatic();
		this.paraIndex = para_index;
		this.recordCaller = new HashSet<SSAInstruction>();
		this.localCollection = new HashSet<SSAInstruction>();
		this.fieldCollection = new HashSet<SSAInstruction>();
		this.specialOuter = new HashSet<SSAInstruction>();
		this.info = new LinkedList<String>();
		this.looper = looper;
		this.loop4ArrayLength = new HashSet<MyTriple>();
		this.relatedConf = new HashSet<String>();
		this.constVar = new HashSet<String>();
		this.cg = cg;
		this.collectionInCallee = new HashSet<MyTriple>();
		this.binaryOpLoop = new HashSet<MyTriple>();
		this.txtPath = txtPath;
		this.whoHasRun = whoHasRun;
		//this.opt = opt;
	}
	
	// when paraIndex = -1, normal case 
	// when paraIndex = others, dInst is a call instruction, and it's from parameter case 
	public HashSet<SSAInstruction> getDataDependences(SSAInstruction dInst){
		System.out.println("slicing for" + dInst.toString());
		HashSet<SSAInstruction> dataSet = new HashSet<SSAInstruction>();
		SSAInstruction tmp = null;
		SSAInvokeInstruction callTmp = null;
		int[] cyx = new int[10];
		if(dInst instanceof SSAInvokeInstruction)
			cyx = ModelLibrary.whichVneedAnalyze((SSAInvokeInstruction) dInst,this.cgNode);
		int cyx_length = 0;
		for(int z : cyx){
			if(z != -1)
				cyx_length++;
		}
		//configuration related 
		//user defined bounded 
		if(SSAUtil.userDefinedBounded(dInst,this.cgNode,this.relatedConf)){
			this.isUserBounded = true;
			return dataSet;
		}
		//get a collection field	
		if(dInst instanceof SSAGetInstruction){
			if(((SSAGetInstruction) dInst).getDeclaredFieldType().getName().toString().matches("Ljava/util/[a-zA-Z]*Set") ||
					((SSAGetInstruction) dInst).getDeclaredFieldType().getName().toString().matches("Ljava/util/[a-zA-Z]*Map") ||
					((SSAGetInstruction) dInst).getDeclaredFieldType().getName().toString().matches("Ljava/util/[a-zA-Z]*List") ||
					((SSAGetInstruction) dInst).getDeclaredFieldType().getName().toString().matches("Ljava/util/[a-zA-Z]*Collection")){
					this.fieldCollection.add(dInst);
					return dataSet;
			}
		}
		
		if(dInst instanceof SSAPutInstruction){
			if(!((SSAPutInstruction) dInst).isStatic()){ //not putstatic
				dataSet.addAll(normalInstCase(dInst,dInst.getUse(1)));
				return dataSet;
			}else{
				dataSet.addAll(normalInstCase(dInst,dInst.getUse(0)));
				return dataSet;
			}
		}
		
		if(dInst instanceof SSABinaryOpInstruction){
			if(((SSABinaryOpInstruction) dInst).getOperator().equals(IBinaryOpInstruction.Operator.ADD)){
				if(SSAUtil.isConstant(this.cgNode,dInst.getUse(1))){
					String op2 = SSAUtil.getConstant(this.cgNode, dInst.getUse(1));
	    			if(!op2.equals("null") && Float.valueOf(op2) > 0 ){
	    				ArrayLengthOp binOp = new ArrayLengthOp(this.cgNode,null,looper);
	    				List<LoopInfo> allLoops = this.looper.findLoopsForCGNode(this.cgNode);
	    				binOp.findLoopForOpInCGN(allLoops,dInst);
	    				if(binOp.loopCond.size() != 0)
	    					this.binaryOpLoop.addAll(binOp.loopCond);
	    			}
	    		}
			}
		}
		
		// new instruction 
		if(dInst instanceof SSANewInstruction){
			
			if(dInst.getNumberOfUses() == 0)
				SSAUtil.markConstant(this.cgNode,dInst,0, this.constVar,true);
			
			if(SSAUtil.newCollection(dInst)){
				this.localCollection.add(dInst);
				HashSet<Integer> retInit =SSAUtil.isCollectionMethodInit(dInst,this.cgNode);
				if(retInit.size() != 0){
					for(Integer Init : retInit){
						dataSet.addAll(normalInstCase(dInst,Init.intValue()));
					}
				}
				
				System.out.println(dInst.toString());
				CollectionViaPara paraCollObj = new CollectionViaPara(dInst.getDef(0),this.cg,this.cgNode,this.looper,this.txtPath,this.whoHasRun);
				paraCollObj.doWork();
				if(paraCollObj.retLoops.size() != 0 )
					this.collectionInCallee.addAll(paraCollObj.retLoops);
				
			}else if(((SSANewInstruction)dInst).getNewSite().getDeclaredType().getName().toString().startsWith("[")){ // new an array 
				System.out.println("it'a an array, determine its length");
				ArrayLengthOp arrayOp = new ArrayLengthOp(this.cgNode,(SSANewInstruction) dInst,looper);
				arrayOp.doWork();
				if(arrayOp.loopCond.size() != 0)
					this.loop4ArrayLength.addAll(arrayOp.loopCond);
				else{
					System.out.println("array length not in a loop");
					HashSet<SSAInstruction> arrayRet = normalInstCase(dInst);
					if(arrayRet.size() == 0){
						this.hasArray = true;
						System.out.println("the length of this array is fixed");
					}
					else
						dataSet.addAll(arrayRet);
				}
			}else if((callTmp = SSAUtil.getInit4New(((SSANewInstruction)dInst).getNewSite().getDeclaredType().getName().toString(), this.cgNode,dInst.getDef(0)))!=null){
				//System.out.println("sbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb" + callTmp);
				String paraTypes = SSAUtil.getParaTypeList(callTmp);
				if(ModelLibrary.isModeled(this.txtPath,(SSAInvokeInstruction) callTmp,this.cgNode,paraTypes)){ // string,path library 
					this.isLibBounded = true;
					this.specialOuter.add(callTmp);
					System.out.println("is 'new' library we modeled");
					System.out.println(dInst.toString());		
					this.info.add("class(new):" + ((SSAInvokeInstruction) callTmp).getDeclaredTarget().getDeclaringClass().getName().toString() + "  method:" +
							((SSAInvokeInstruction) callTmp).getDeclaredTarget().getName().toString() + " from new instruction:"+ dInst.toString() + 
							" info for this new: class" + this.cgNode.getMethod().getDeclaringClass().getName().toString() +" method: " + this.cgNode.getMethod().getName().toString() 
							+ " line:" + IRUtil.getSourceLineNumberFromSSA(this.cgNode,dInst)+ " starter:" + this.starter + " paraLoc:" + this.exactPara + "--->");
					return dataSet;
				}else{
					//for(int k = 1 ; k < callTmp.getNumberOfUses();k++)
					//	dataSet.addAll(normalInstCase(callTmp,callTmp.getUse(k)));
				}
			}
			else{
				System.out.println("corner case 1 in backwardslicing");
				assert 0 == 1;
				dataSet.addAll(normalInstCase(dInst));
			}
			return dataSet;
		}
		if(dInst instanceof SSAInvokeInstruction){
			//System.out.println(dInst.toString());
			//System.out.println(((SSAInvokeInstruction)dInst).getDeclaredTarget().getDeclaringClass().getClassLoader().getName().toString());
			//System.out.println("paraIndex is ? " + this.paraIndex);
			//System.out.println(dInst.getNumberOfUses());
			//System.out.println(dInst.toString());
			//assert ((SSAInvokeInstruction)dInst).getDeclaredTarget().getDeclaringClass().getClassLoader().getName().toString().equals("Application");	
			
		//	this.info.add("class:" + ((SSAInvokeInstruction) dInst).getDeclaredTarget().getDeclaringClass().getName().toString() + "method:" +
		//			((SSAInvokeInstruction) dInst).getDeclaredTarget().getName().toString() + "--->");
			
			if( this.paraIndex == -1 || this.recordCaller.contains(dInst)){
				if(SSAUtil.isCollectionEmpty((SSAInvokeInstruction) dInst)){
					this.isEmptyColl = true;
					return dataSet;
				}
				
				// normal dInst is a call instruction, but it's not from parameter case 
				// collection method, like size(), length()....
				// or iterator method, like next(), hasNext()...			
				String dInstParaTypes = SSAUtil.getParaTypeList((SSAInvokeInstruction) dInst);
				
				//special collection from com.google.common.collect

				if(((SSAInvokeInstruction)dInst).getDeclaredTarget().getDeclaringClass().getName().toString().equals("Lcom/google/common/collect/HashMultimap") &&
						((SSAInvokeInstruction)dInst).getDeclaredTarget().getName().toString().equals("create")){
						this.localCollection.add(dInst);
						return dataSet;
					}
				if(SSAUtil.isCollectionMethod(dInst) || ModelLibrary.isNormalIteratorMethod((SSAInvokeInstruction) dInst) ){
					// collection
					System.out.println("isCollectionMethod or normal iterator method");
					SSAInstruction cTmp = null;
					System.out.println("xxxxx" + dInst.toString());
					
			
					if(dInst.getNumberOfUses() >0 && (cTmp = SSAUtil.getSSAByDU(this.cgNode,dInst.getUse(0))) != null){
						if(cTmp instanceof SSANewInstruction){ //local collection
							this.localCollection.add(dInst);
							HashSet<Integer> retInit2 =SSAUtil.isCollectionMethodInit(dInst,this.cgNode);
							if(retInit2.size() != 0){
								for(Integer Init2 : retInit2){
									dataSet.addAll(normalInstCase(dInst,Init2.intValue()));
								}
							}
						}
						else if(cTmp instanceof SSAGetInstruction){ //field collection
							this.fieldCollection.add(cTmp);
						}else{
							dataSet.add(cTmp);
							if(cTmp instanceof SSAInvokeInstruction)
								this.recordCaller.add(cTmp);
							//System.out.println(dInst.toString());
							//System.out.println(cTmp.toString());
							//System.out.println("corner case 2 in backwardslicing");
						}
					}else{
						if(SSAUtil.isConstant(this.cgNode,dInst.getUse(0)))
							SSAUtil.markConstant(this.cgNode,dInst,dInst.getUse(0), constVar,true);
						
						if(SSAUtil.isVnParameter(this.cgNode,dInst.getUse(0))){	
							if(!this.cgNode.getMethod().getName().toString().equals("main"))
								this.paraList.add(SSAUtil.getParameterLoc(dInst.getUse(0), this.cgNode)); //A(a,b,c){}, for a,b,c, which parameter we need analyze 
						}else{
							dataSet.addAll(normalInstCase(dInst));
							System.out.println("corner case 3 in backwardslicing");
							System.out.println(dInst.toString());
							//assert 0==1;
						}
					}
				}else if(cyx_length != 0){ // the library from commonLib.txt, use it before ModelLibrary
					System.out.println("is common lib we modeled");
					for(int c : cyx){
						if(c != -1)
							dataSet.addAll(normalInstCase(dInst,dInst.getUse(c)));
					}
				}else if(ModelLibrary.isModeled(this.txtPath,(SSAInvokeInstruction) dInst,this.cgNode,dInstParaTypes)){ // string,path library 
					System.out.println("is library we modeled");
					System.out.println(dInst.toString());
					this.isLibBounded = true;
					this.specialOuter.add(dInst);
					if(((SSAInvokeInstruction) dInst).isStatic()){
						this.info.add("class(static):" + ((SSAInvokeInstruction) dInst).getDeclaredTarget().getDeclaringClass().getName().toString() + "  method:" +
								((SSAInvokeInstruction) dInst).getDeclaredTarget().getName().toString() + " from call instruction:"+ dInst.toString() + 
								" info for this call: class" + this.cgNode.getMethod().getDeclaringClass().getName().toString() +" method: " + this.cgNode.getMethod().getName().toString() 
								+ " line:" + IRUtil.getSourceLineNumberFromSSA(this.cgNode,dInst)+ " starter:" + this.starter + " paraLoc:" + this.exactPara +  "--->");
						return dataSet;
					}else{
						this.info.add("class(non-static):" + ((SSAInvokeInstruction) dInst).getDeclaredTarget().getDeclaringClass().getName().toString() + "  method:" +
								((SSAInvokeInstruction) dInst).getDeclaredTarget().getName().toString() + " from call instruction"+ dInst.toString() + 
								" info for this call: class" + this.cgNode.getMethod().getDeclaringClass().getName().toString() +" method: " + this.cgNode.getMethod().getName().toString() 
								+ " line:" + IRUtil.getSourceLineNumberFromSSA(this.cgNode,dInst)+ " starter:" + this.starter + " paraLoc:" + this.exactPara +  "--->");
						//dataSet.addAll(normalInstCase(dInst,dInst.getUse(0)));
						return dataSet;
						//this.specialOuter.add(dInst);
						//return dataSet;
					}
				}else if(ModelLibrary.isPrimordial(dInst,this.cgNode)){
					//this.isLibBounded = true;
					System.out.println("isPrimordial function call");
					System.out.println(dInst.toString());
					dataSet.addAll(normalInstCase(dInst));
				//}else if(((SSAInvokeInstruction)dInst).isStatic() && ((SSAInvokeInstruction)dInst).getDeclaredTarget().getName().toString().contains("access$")){
					// static class field 
				//	dataSet.addAll(normalInstCase(dInst));
				/*}else if(((SSAInvokeInstruction) dInst).getDeclaredTarget().getDeclaringClass().getName().toString().startsWith("Ljava/lang/Math")&&
						(((SSAInvokeInstruction) dInst).getDeclaredTarget().getName().toString().equals("min"))){
					  dataSet.addAll(normalInstCase(dInst,dInst.getUse(0)));
					  dataSet.addAll(normalInstCase(dInst,dInst.getUse(1)));*/
			
				}else if(((SSAInvokeInstruction)dInst).getDeclaredTarget().getReturnType().getName().toString().equals("Z")
						|| ((SSAInvokeInstruction)dInst).getDeclaredTarget().getReturnType().getName().toString().equals("V")){
						System.out.println("it's bounded because returns void or boolean");
				}else if(!((SSAInvokeInstruction)dInst).getDeclaredTarget().getReturnType().getName().toString().equals("Z")
						&& !((SSAInvokeInstruction)dInst).getDeclaredTarget().getReturnType().getName().toString().equals("V")){
					//exclude the case, which return value is boolean 
					
					/*if(((SSAInvokeInstruction)dInst).getDeclaredTarget().getDeclaringClass().getClassLoader().getName().toString().equals("Primordial")){ // it's a library call, analyze its parameters?
						 dataSet.addAll(normalInstCase(dInst));
					}else{*/
						System.out.println("normal ret case");
						if(((SSAInvokeInstruction) dInst).getDeclaredTarget().getName().toString().equals("toArray") && 
								((SSAInvokeInstruction) dInst).getDeclaredTarget().getDeclaringClass().getName().toString().contains("Ljava/util")){
							dataSet.addAll(normalInstCase(dInst,dInst.getUse(0)));
						//}else if(((SSAInvokeInstruction) dInst).getDeclaredTarget().getDeclaringClass().getName().toString().contains("Lorg/apache/hadoop/io/WritableUtils")){
						//	if(dInst.getNumberOfUses() >=1)
						//		dataSet.addAll(normalInstCase(dInst,dInst.getUse(0)));
						}else{
							if(((SSAInvokeInstruction) dInst).isStatic()){
								if(!SSAUtil.otherFileSystem(((SSAInvokeInstruction) dInst).getDeclaredTarget().getDeclaringClass().getName().toString()))
									this.callRet.add(dInst);
							}
							else{
							//dataSet.addAll(normalInstCase(dInst,dInst.getUse(0)));
								if(!SSAUtil.otherFileSystem(((SSAInvokeInstruction) dInst).getDeclaredTarget().getDeclaringClass().getName().toString()))
									this.callRet.add(dInst);
							}
						}
						//this.outerInst.add(dInst);
						//this.specialOuter.add(dInst);
					//}
					//}
				}else{
					System.out.println("wtf.....");
					System.out.println(dInst.toString());
					assert 0 == 1;
					dataSet.addAll(normalInstCase(dInst));
				}
				return dataSet;
			}else{ // call instruction, we need focus on specific parameter 
				System.out.println(this.paraIndex);
				assert this.paraIndex >= 0;
				//System.out.println("xxxx" + this.paraIndex);
				//System.out.println("xxxx" + dInst.getNumberOfUses());
				int paraIndexInSSA = dInst.getUse(this.paraIndex);
				if(SSAUtil.isConstant(this.cgNode,paraIndexInSSA)){
					System.out.println("constant for specific parameter ");
					if(((SSAInvokeInstruction) dInst).isStatic()){
						this.constVar.add((this.paraIndex + 1) + "-th parameter: " + SSAUtil.getConstant(this.cgNode,paraIndexInSSA));
					}else
						this.constVar.add(this.paraIndex + "-th parameter: " + SSAUtil.getConstant(this.cgNode,paraIndexInSSA));
					//this.specialOuter.add(dInst);
					//this.outerInst.add(dInst);
					// do nothing 
				}else if(SSAUtil.isVnParameter(this.cgNode,paraIndexInSSA)){
					if(!this.cgNode.getMethod().getName().toString().equals("main")){
						System.out.println("parameter for specific parameter");
						this.paraList.add(SSAUtil.getParameterLoc(paraIndexInSSA,this.cgNode));
					}
				}else if((tmp = SSAUtil.getSSAByDU(this.cgNode,paraIndexInSSA))!=null){
					System.out.println("normal case for specific parameter");
					dataSet.add(tmp);
					if(tmp instanceof SSAInvokeInstruction)
						this.recordCaller.add(tmp);
				}else{
					//System.out.println("corner case 4 in backwardslicing");
					//System.out.println(dInst.toString());
					//assert 0 == 1;
					dataSet.addAll(normalInstCase(dInst,dInst.getUse(this.paraIndex)));
				}
				return dataSet;
			}
		}
		dataSet.addAll(normalInstCase(dInst));
		return dataSet;
	}
	
	public HashSet<SSAInstruction> normalInstCase(SSAInstruction dInst){
		System.out.println("normal inst case" + dInst.toString());
		HashSet<SSAInstruction> dataSet = new HashSet<SSAInstruction>();
		SSAInstruction tmp = null;
		int numOfUse = dInst.getNumberOfUses();
		
		//if(dInst instanceof SSAInvokeInstruction){
		//	this.info.add("class:" + ((SSAInvokeInstruction) dInst).getDeclaredTarget().getDeclaringClass().getName().toString() + "method:" +
		//			((SSAInvokeInstruction) dInst).getDeclaredTarget().getName().toString() + "--->");
		//}
		if(numOfUse == 0){
			this.outerInst.add(dInst);
			SSAUtil.markConstant(this.cgNode,dInst,0, constVar,false);
			System.out.println(dInst.toString() + "doesn't have use!!!");
		}
		for(int i = 0; i < numOfUse; i++){
			int index = dInst.getUse(i);
			//System.out.println(index+"xxxxxxxxxxxxxxxx");
			//SSAInstruction ssa_tmp = SSAUtil.getSSAIndexByDefvn(this.allInsts,index);
			SSAInstruction ssa_tmp = SSAUtil.getSSAByDU(this.cgNode,index);
			if(ssa_tmp != null){
				dataSet.add(ssa_tmp);
				if(ssa_tmp instanceof SSAInvokeInstruction)
					this.recordCaller.add(ssa_tmp);
			}
			if(ssa_tmp == null){
				if(SSAUtil.isConstant(this.cgNode,index))
					SSAUtil.markConstant(this.cgNode,dInst,index, constVar,true);
				
				if(!(dInst instanceof SSAPhiInstruction) && !(dInst instanceof SSAPutInstruction)) // handle phi instruction, no need analyze it, because we would analyze all its uses
																								    // no need to handle putfield,b/c no others can affect it, it's a overwritten 
					this.outerInst.add(dInst);
				if(SSAUtil.isVnParameter(this.cgNode, index)){
					if(!this.cgNode.getMethod().getName().toString().equals("main") && !this.cgNode.getMethod().toString().contains("access$")){
						System.out.println(this.cgNode.toString());
						this.paraList.add(SSAUtil.getParameterLoc(index,this.cgNode));
					}
				}
				if((!this.inStatic && index == 1 && (dInst instanceof SSAGetInstruction)) ||
						this.inStatic && index == 1 && this.cgNode.getMethod().getName().toString().contains("access$") && (dInst instanceof SSAGetInstruction)){
					this.thisList.add(dInst);
				}
			}
		}
		return dataSet;
	}
	
	public HashSet<SSAInstruction> normalInstCase(SSAInstruction dInst, int i){
		System.out.println("in normalInstCase");
		System.out.println(dInst.toString());
		System.out.println(i);
		//if(dInst instanceof SSAInvokeInstruction){
		//	this.info.add("class:" + ((SSAInvokeInstruction) dInst).getDeclaredTarget().getDeclaringClass().getName().toString() + "method:" +
		//			((SSAInvokeInstruction) dInst).getDeclaredTarget().getName().toString() + "--->");
		//}
		
		HashSet<SSAInstruction> dataSet = new HashSet<SSAInstruction>();
		SSAInstruction tmp = null;
			//SSAInstruction ssa_tmp = SSAUtil.getSSAIndexByDefvn(this.allInsts,index);
		SSAInstruction ssa_tmp = SSAUtil.getSSAByDU(this.cgNode,i);
		if(ssa_tmp != null){
			dataSet.add(ssa_tmp);
			if(ssa_tmp instanceof SSAInvokeInstruction)
				this.recordCaller.add(ssa_tmp);
		}
		if(ssa_tmp == null){
			if(SSAUtil.isConstant(this.cgNode, i))
				SSAUtil.markConstant(this.cgNode,dInst,i, constVar,true);
			if(!(dInst instanceof SSAPhiInstruction) && !(dInst instanceof SSAPutInstruction)) // handle phi instruction, no need analyze it, because we would analyze all its uses
																								    // no need to handle putfield,b/c no others can affect it, it's a overwritten 
				this.outerInst.add(dInst);
			if(SSAUtil.isVnParameter(this.cgNode, i)){
				if(!this.cgNode.getMethod().getName().toString().equals("main") && !this.cgNode.getMethod().toString().contains("access$")){
					System.out.println(this.cgNode.toString());
					this.paraList.add(SSAUtil.getParameterLoc(i,this.cgNode));
				}
			}
			if((!this.inStatic && i == 1 && (dInst instanceof SSAGetInstruction)) ||
					this.inStatic && i == 1 && this.cgNode.getMethod().getName().toString().contains("access$") && (dInst instanceof SSAGetInstruction))
				this.thisList.add(dInst);
		}
		return dataSet;
	}
	
	
	//include dsInst itself 
	public void getDataSlicing(SSAInstruction dsInst){
		
		this.starter = dsInst;
		this.exactPara = SSAUtil.paraLocTrans(this.starter,this.cgNode,this.paraIndex);
		if(SSAUtil.otherFileSystem(this.cgNode)){
			System.out.println("we only focus on distrubuted file system");
			return;
		}
		System.out.println("&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&");
		System.out.println("class name" + ":" + this.cgNode.getMethod().getDeclaringClass().getName().toString());
		System.out.println("method name" + ":" + this.cgNode.getMethod().getName().toString());
		System.out.println("SSAInstruction" + ":" + dsInst.toString());
		System.out.println("line number" + ":" + IRUtil.getSourceLineNumberFromSSA(this.cgNode,dsInst));
		System.out.println("para Loc" + this.exactPara);
		System.out.println("&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&");
		
		
		LinkedList<SSAInstruction> work_set = new LinkedList<SSAInstruction>();
		HashSet<SSAInstruction> visited_set = new HashSet<SSAInstruction>();
		work_set.add(dsInst);
		visited_set.add(dsInst);
		this.slicer.add(dsInst);
		while(!work_set.isEmpty()){
			SSAInstruction tmpInst = work_set.poll();
			HashSet<SSAInstruction> dSet = getDataDependences(tmpInst);
			for(SSAInstruction newInst : dSet){
				if(newInst instanceof SSAInvokeInstruction){ // don't analyze the library we modeled, just stop here 
					int[] cyx = new int[10];
					if(newInst instanceof SSAInvokeInstruction)
						cyx = ModelLibrary.whichVneedAnalyze((SSAInvokeInstruction) newInst,this.cgNode);
					int cyx_length = 0;
					for(int z : cyx){
						if(z != -1)
							cyx_length++;
					}
					
					if(cyx_length != 0){
						System.out.println("common library, need analyze some specific uses");
					}
					else{
						String dInstParaTypes = SSAUtil.getParaTypeList((SSAInvokeInstruction)newInst);
						if(ModelLibrary.isModeled(this.txtPath,(SSAInvokeInstruction)newInst,this.cgNode,dInstParaTypes)){
							System.out.println("we model it, no need analyze any more" + newInst.toString());
							if(((SSAInvokeInstruction) newInst).isStatic()){
								this.info.add("class(static):" + ((SSAInvokeInstruction) newInst).getDeclaredTarget().getDeclaringClass().getName().toString() + "  method:" +
										((SSAInvokeInstruction) newInst).getDeclaredTarget().getName().toString() + " from call instruction:"+ newInst.toString() + 
										" info for this call: class" + this.cgNode.getMethod().getDeclaringClass().getName().toString() +" method: " + this.cgNode.getMethod().getName().toString() 
										+ " line:" + IRUtil.getSourceLineNumberFromSSA(this.cgNode,newInst)+ " starter:" + this.starter + " paraLoc:" + this.exactPara +  "--->");
							}else{
								this.info.add("class(non-static):" + ((SSAInvokeInstruction) newInst).getDeclaredTarget().getDeclaringClass().getName().toString() + "  method:" +
										((SSAInvokeInstruction) newInst).getDeclaredTarget().getName().toString() + " from call instruction"+ newInst.toString() + 
										" info for this call: class" + this.cgNode.getMethod().getDeclaringClass().getName().toString() +" method: " + this.cgNode.getMethod().getName().toString() 
										+ " line:" + IRUtil.getSourceLineNumberFromSSA(this.cgNode,newInst)+ " starter:" + this.starter + " paraLoc:" + this.exactPara +  "--->");
							}
							visited_set.add(newInst);
						}
					}
				}
				if(!visited_set.contains(newInst)){
					this.slicer.add(newInst);
					work_set.add(newInst);
					visited_set.add(newInst);
				}
			}
			System.out.println("working in backward slicing");
		}
	}
	
	public void printSlice(){
		System.out.println("#####################################################");
		System.out.println("print slicing result");
		for(SSAInstruction tmpInst:this.slicer)
			System.out.println(tmpInst.toString());
		System.out.println("#####################################################");
		System.out.println("print outer slicing result");
		for(SSAInstruction aInst:this.outerInst)
			System.out.println(aInst.toString());
		System.out.println("#####################################################");
		System.out.println("print callRet in backwardslicing");
		for(SSAInstruction tmp: this.callRet)
			System.out.println(tmp.toString());
		System.out.println("#####################################################");
		System.out.println("print paraList in backwardslicing");
		for(Integer tmp:this.paraList)
			System.out.println(tmp.intValue());
		System.out.println("#####################################################");
		System.out.println("print thisList in backwardslicing");
		for(SSAInstruction tmp : this.thisList)
			System.out.println(tmp.toString());
		System.out.println("#####################################################");
		System.out.println("print local collection in backwardslicing");
		for(SSAInstruction tmp : this.localCollection)
			System.out.println(tmp.toString());
		System.out.println("#####################################################");
		System.out.println("print field collection in backwardslicing");
		for(SSAInstruction tmp : this.fieldCollection)
			System.out.println(tmp.toString());
		System.out.println("#####################################################");
		System.out.println("print loop for array in backwardslicing");
		for(MyTriple tmp : this.loop4ArrayLength)
			System.out.println(tmp.getSSA().toString());
		System.out.println("#####################################################");
	}
	
	public boolean isBoundedCondInThisCFG(){
		//no need to analyze function call, parameter, and class field
		// and for every instruction in outerInst, all its uses are constant 
		// then it's bounded 
		// otherwise, we need analyze function call, parameter, and class field 
		if(this.callRet.size() == 0 && this.paraList.size() == 0 && this.thisList.size() == 0 && this.loop4ArrayLength.size() == 0 && this.collectionInCallee.size() == 0 && this.info.size() == 0 && this.binaryOpLoop.size()==0){
			for(SSAInstruction ssa_tmp : this.outerInst){
				if(this.specialOuter.contains(ssa_tmp)) 
					continue;
				for(int i = 0; i < ssa_tmp.getNumberOfUses();i++){
					//if(SSAUtil.getSSAIndexByDefvn(this.ir.getInstructions(),ssa_tmp.getUse(i)) == null){
					if(SSAUtil.getSSAByDU(this.cgNode,ssa_tmp.getUse(i)) == null){
						if(SSAUtil.isConstant(this.cgNode,ssa_tmp.getUse(i))){
							SSAUtil.markConstant(this.cgNode, ssa_tmp, i, this.constVar,false);
							continue;
						}
						else{
							if(this.cgNode.getMethod().getName().toString().equals("main") && SSAUtil.isVnParameter(this.cgNode,ssa_tmp.getUse(i)))
								continue;
							System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~not a constant~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
							System.out.println(ssa_tmp.toString());
							System.out.println(ssa_tmp.getUse(i));
							System.out.println(SSAUtil.getSSAByDU(this.cgNode,ssa_tmp.getUse(i)));
							return false;
						}
					}
				}
			}
			return true;
		}
		return false;
	}
	
	

}
