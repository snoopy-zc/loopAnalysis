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
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.shrikeBT.IBinaryOpInstruction;
import com.ibm.wala.shrikeBT.IConditionalBranchInstruction;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAArrayLengthInstruction;
import com.ibm.wala.ssa.SSABinaryOpInstruction;
import com.ibm.wala.ssa.SSACFG;
import com.ibm.wala.ssa.SSAComparisonInstruction;
import com.ibm.wala.ssa.SSAConditionalBranchInstruction;
import com.ibm.wala.ssa.SSAFieldAccessInstruction;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SSAGotoInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.ssa.SSAPhiInstruction;
import com.ibm.wala.ssa.SSAPutInstruction;
import com.ibm.wala.ssa.SSAReturnInstruction;
import com.ibm.wala.ssa.SymbolTable;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.util.WalaException;

import sa.loop.LoopAnalyzer;
import sa.loop.LoopInfo;
import sa.wala.IRUtil;

public final class SSAUtil {
	
	public static void testDef(SSAInstruction ssaInst){
		int numOfDef = ssaInst.getNumberOfDefs();
		for(int i = 0; i < numOfDef; i++){
			System.out.println("getDef" + i +" : " +ssaInst.getDef(i));
		}
	}
	public static void testUse(SSAInstruction ssaInst){
		int numOfDef = ssaInst.getNumberOfUses();
		for(int i = 0; i < numOfDef; i++){
			System.out.println("getUse" + i + ":" +ssaInst.getUse(i));
		}
	}

	  // find data dependence of specific variable 
	  // op v1, v2 ---> v2 = xxx
	 // can not find phi node
	 /* public static SSAInstruction getSSAIndexByDefvn(SSAInstruction[] ssas, int defvn) {
	    int index = -1;
	    int count = 0;
	    SSAInstruction ssaInst = null;
	    for (int i = 0; i < ssas.length; i++)
	      if (ssas[i] != null) {
	    	assert count <= 1;
	    	if (ssas[i].hasDef() && ssas[i].getDef() == defvn){
	    		index = i;
	    		count++;
	    		ssaInst = ssas[i];
	    	}
	    }
	    return ssaInst;
	  }*/
	  
	  public static SSAInstruction getSSAByDU(CGNode cg, int def){
		  SSAInstruction ssa_tmp = null;
		  ssa_tmp = cg.getDU().getDef(def);
		  return ssa_tmp;
	  }
	  
	  public static void printAllInsts(SSAInstruction[] ssas){
		  System.out.println("begin to print ssa instructions");
		  for(int i = 0; i < ssas.length; i++){
			  if(ssas[i] != null){
				  System.out.println(ssas[i].toString());
			  }
		  }
	  }
	 
	  // determine a variable is parameter 
	 public static boolean isVnParameter(CGNode function, int vn) {
		    IMethod im = function.getMethod();
		    IR ir = function.getIR();
		    if (!im.isStatic() && 2<= vn &&  vn <= ir.getNumberOfParameters()
		        || im.isStatic() && vn <= ir.getNumberOfParameters()){
		      return true;
		    }
		    return false;
	 } 
	 
	 // determine a variable is constant(including boolen, int, floag...) or not 
	 public static boolean isConstant(CGNode function, int vn){
		 	IMethod im = function.getMethod();
		 	IR ir = function.getIR();
		    SymbolTable symboltable = ir.getSymbolTable();                                                 //JX: seems same??
		    //System.out.println("ir.getSymbolTable: " + symboltable);
		    //System.out.println(symboltable.getNumberOfParameters());
		    //for (int i=0; i<symboltable.getNumberOfParameters(); i++)
		     // System.out.println(symboltable.getParameter(i)); 
		   // if(symboltable.isIntegerConstant(vn)){
		    //	System.out.println(symboltable.getIntValue(vn));
		   //return symboltable.isFloatConstant(vn);
			//}
			return symboltable.isConstant(vn);
	 }
	 
	 public static String getConstant(CGNode function, int vn){
		// assert isConstant(function,vn);
		 if(isConstant(function,vn)){
			 IR ir = function.getIR();
			 SymbolTable symboltable = ir.getSymbolTable();
			 //ret.put(symboltable. ,symboltable.getValueString(vn))
			 //return symboltable.getValueString(vn);
			 if(symboltable.getConstantValue(vn) == null)
				 return "null";
			 else
				 return symboltable.getConstantValue(vn).toString();
		}else
			return "nonConstant";
	 }
	 
	 public static String[] getConstantVarName(CGNode function,SSAInstruction ssa, int vn){
		 IR ir = function.getIR();
		 int x = IRUtil.getSSAIndex(function, ssa);
		 System.out.println(function.getMethod().getName().toString());
		 if(x == -1)
			 return null;
		 return ir.getLocalNames(x, vn);
		 //ir.getLocalNames(index, vn)
	 }
	 //return 1 this.filed
	 //return 2 parameter.field
	 public int complexType(SSAInstruction inst){
		 if(inst instanceof SSAFieldAccessInstruction)
			 return 1;
		 return -1;
	 }
	 
	 //for a field access, determine it's which class's field 
	 public static String fieldOfWhichClass(SSAFieldAccessInstruction finst){
		if (finst instanceof SSAGetInstruction){
			//System.out.println("getfield");
			return finst.getDeclaredField().getDeclaringClass().getName().toString();
			//System.out.println(finst.toString());
			//System.out.println(finst.getDeclaredField().getDeclaringClass().getName().toString()); // return class name 
			//System.out.println(finst.getDeclaredField().getName().toString()); // return this variable name 
			//System.out.println(finst.getDeclaredFieldType().toString());
			//System.out.println(finst.getUse(0));

		}
		if (finst instanceof SSAPutInstruction){
			//System.out.println("putfield");
			return finst.getDeclaredField().getDeclaringClass().getName().toString();
			//System.out.println(finst.getDeclaredField().getDeclaringClass().getName().toString()); // return class name 
			//System.out.println(finst.getDeclaredField().getName().toString()); // return this variable name\
		}
		return null;
	 }
	 
	 //for a field access, determine it's which class's field 
	 public static String fieldOfWhichVar(SSAFieldAccessInstruction finst){
		if (finst instanceof SSAGetInstruction){
			//System.out.println("getfield");
			return finst.getDeclaredField().getName().toString();
			//System.out.println(finst.toString());
			//System.out.println(finst.getDeclaredField().getDeclaringClass().getName().toString()); // return class name 
			//System.out.println(finst.getDeclaredField().getName().toString()); // return this variable name 
			//System.out.println(finst.getDeclaredFieldType().toString());
			//System.out.println(finst.getUse(0));

		}
		if (finst instanceof SSAPutInstruction){
			//System.out.println("putfield");
			return finst.getDeclaredField().getName().toString();
			//System.out.println(finst.getDeclaredField().getDeclaringClass().getName().toString()); // return class name 
			//System.out.println(finst.getDeclaredField().getName().toString()); // return this variable name\
		}
		return null;
	 }
	 
	 //get all return instructions inside one method 
	 public static HashSet<SSAInstruction> getAllRetInst(IR ir){
		 HashSet<SSAInstruction> retInsts = new HashSet<SSAInstruction>();
		 SSACFG cfg = ir.getControlFlowGraph();
	 	 for (Iterator<ISSABasicBlock> ibb = cfg.iterator(); ibb.hasNext(); ) {
	 		 ISSABasicBlock bb = ibb.next();
	 		 for (Iterator<SSAInstruction> issa = bb.iterator(); issa.hasNext(); ) {
	 			 SSAInstruction tmp_ssa = issa.next();
	 			 if(tmp_ssa instanceof SSAReturnInstruction){
	 				 retInsts.add(tmp_ssa);  
	 	    	 }
	 	     }
	 	 }
	 	 return retInsts;
	 }
	 
	 //for a parameter, get its location is parameter array 
	 public static int getParameterLoc(int num, CGNode node){
		 if(node.getMethod().isStatic())
			 return num;
		 else
			 return num-1;
	 }
	 
	 //determine a conditional branch is Colleciton-like branch
	 public static SSAInstruction isCollection(CGNode cgNode, SSAInstruction ssaInst,CallGraph cg,LoopAnalyzer looper,HashSet<MyTriple> userIter,String txtPath,HashSet<MyPair>whoHasRun){
		 //*1*. have to successor basicblocks, one should be like 
		 //9 v10 = invokeinterface < Application, Ljava/util/Iterator, next()Ljava/lang/Object; > v5 @18 exception:v9
		 //the other one should be like return 
		 //*2*. have one predecessor basicblocks, it should be like
		 //5 v7 = invokeinterface < Application, Ljava/util/Iterator, hasNext()Z > v5 @9 exception:v6
		 // and this basicblocks should have predecessor 2 v5 = invokevirtual < Application, Ljava/util/HashSet, iterator()Ljava/util/Iterator; > v3 @4 exception:v4
		 // and its predecessor basicblocks, it should be like 
		 // 1 v3 = getfield < Application, LTestCase, mySet, <Application,Ljava/util/HashSet> > v1
		 SSAInstruction retInst = null;
		 SSAInstruction tmpInst = null;
		 IR ir = cgNode.getIR();
		 SSACFG cfg = ir.getControlFlowGraph();
		 //System.out.println("xxxxxx" + ssaInst.toString());
		 if(isSecondUseZero(ssaInst.getUse(1),cgNode)){
			 System.out.println("sbbb1");
			 ISSABasicBlock ibb = ir.getBasicBlockForInstruction(ssaInst);
			 if(isCollectionSucc(ibb,cfg)){
				 System.out.println("sbbb2");
				 if( (tmpInst = isCollectionPred(ibb,cfg,cgNode,cg,looper,userIter,txtPath,whoHasRun)) != null){
					 System.out.println("sbbbb3");
					 retInst = tmpInst;
				 }
			 }
		 }
		 return retInst;
	 }
	 
	 public static boolean isSecondUseZero(int num, CGNode cgN){
		 IMethod im = cgN.getMethod();
		 IR ir = cgN.getIR();
		 SymbolTable symboltable = ir.getSymbolTable();
		 if(isConstant(cgN,num)){
			 if(symboltable.isIntegerConstant(num) && symboltable.getIntValue(num) == 0)
				 return true;
		 }
		 return false;
	 }
	 
	 public static boolean isCollectionSucc(ISSABasicBlock ibb, SSACFG cfg){
		 boolean retVal = false;
		 ISSABasicBlock[] mybb = new ISSABasicBlock[2];
		 int i = 0;
		 System.out.println(cfg.getSuccNodeCount(ibb));
		 if(cfg.getSuccNodeCount(ibb) == 2){
			 for(ISSABasicBlock bbb : cfg.getNormalSuccessors(ibb)){
				 mybb[i++] = bbb;
			 }
			/* System.out.println(mybb[0].toString());
			 System.out.println(mybb[1].toString());
			 System.out.println(isCollectionRetNone(mybb[0]));
			 System.out.println(isCollectionNext(mybb[1])); 
			 System.out.println("xxxx"); 
			 System.out.println(isCollectionRetNone(mybb[1]));
			 System.out.println(isCollectionNext(mybb[0]));*/
			// if((isCollectionNext(mybb[0]) && isCollectionRetNone(mybb[1])) ||
			//		 (isCollectionNext(mybb[1]) && isCollectionRetNone(mybb[0])))
				 	// System.out.println("fuck");
			 if(isCollectionNext(mybb[0]) || isCollectionNext(mybb[1]))
					 retVal = true;
		 }
		 return retVal;
	 }
	 
	 public static boolean isCollectionNext(ISSABasicBlock ibb){
		 boolean retVal = false;
		 SSAInstruction ssa_tmp = null;
		 int i = 0;
		 for(SSAInstruction ssa: ibb){
			ssa_tmp = ssa;
			i++;
		 }
		 if(i == 1){
			 if(ssa_tmp instanceof SSAInvokeInstruction){
				 if((((SSAInvokeInstruction) ssa_tmp).getDeclaredTarget().getDeclaringClass().getName().toString().equals("Ljava/util/Iterator") &&
						 ((SSAInvokeInstruction) ssa_tmp).getDeclaredTarget().getName().toString().equals("next") &&
						 ((SSAInvokeInstruction) ssa_tmp).getDeclaredTarget().getReturnType().getName().toString().equals("Ljava/lang/Object") &&
						 ((SSAInvokeInstruction) ssa_tmp).isDispatch()) ||
						 (((SSAInvokeInstruction) ssa_tmp).getDeclaredTarget().getDeclaringClass().getName().toString().contains("Iterator") &&
								 ((SSAInvokeInstruction) ssa_tmp).getDeclaredTarget().getName().toString().equals("next") &&
								 ((SSAInvokeInstruction) ssa_tmp).isDispatch()) )
					 retVal = true;
				 //System.out.println("sbbbbbb" + ssa_tmp.toString());
				 //5 v7 = invokeinterface < Application, Ljava/util/Iterator, hasNext()Z > v5 @9 exception:v6
				 //System.out.println(((SSAInvokeInstruction) ssa_tmp).getDeclaredTarget().getName().toString());
				 //print hasNext
				 //System.out.println(((SSAInvokeInstruction) ssa_tmp).getDeclaredTarget().getReturnType().getName().toString());
				 //System.out.println(((SSAInvokeInstruction) ssa_tmp).getDeclaredTarget().getDeclaringClass().getName().toString());
				 //print Ljava/util/Iterator
				 //System.out.println(((SSAInvokeInstruction) ssa_tmp).getDeclaredTarget().getDeclaringClass().getClassLoader().equals(ClassLoaderReference.Application));
				 //print Application
			 }
		 }
		 return retVal;
	 }
	 
	 public static boolean isCollectionRetNone(ISSABasicBlock ibb){
		 boolean retVal = false;
		 SSAInstruction ssa_tmp = null;
		 int i = 0;
		 for(SSAInstruction ssa: ibb){
			ssa_tmp = ssa;
			i++;
		 }
		 if(i == 1){
			 if(ssa_tmp instanceof SSAReturnInstruction){
				 // System.out.println(ssa_tmp.toString());
				  if(((SSAReturnInstruction) ssa_tmp).returnsVoid())
					  retVal = true;
			 }
		 }
		 return retVal;
	 }
	 
	 public static SSAInstruction isCollectionPred(ISSABasicBlock ibb,SSACFG cfg,CGNode cgNode,CallGraph cg,LoopAnalyzer looper,HashSet<MyTriple> userIter, String txtPath, HashSet<MyPair>whoHasRun){
		 SSAInstruction retInst = null;
		 boolean retVal1 = false;
		 boolean retVal2 = false;
		 boolean retVal3 = false;
		 int i1 = 0;
		 SSAInstruction ssa_tmp1 = null;
		 ISSABasicBlock ibb1 = null;
		 // BB4 5 v7 = invokeinterface < Application, Ljava/util/Iterator, hasNext()Z > v5 @9 exception:v
		 System.out.println(ibb.toString());
		 System.out.println(cfg.getPredNodeCount(ibb));
		 if(cfg.getPredNodeCount(ibb) == 1){
			 Collection<ISSABasicBlock> pred = cfg.getNormalPredecessors(ibb);
			 for(ISSABasicBlock iiib : pred){
				 ibb1 = iiib;
				 for(SSAInstruction inst: iiib){
					 if(inst instanceof SSAPhiInstruction || inst == null)
						 continue;
					 i1++;
					 ssa_tmp1 = inst;
				 }
			 }
		 }
		 System.out.println(i1);
		 System.out.println(ssa_tmp1.toString());
		 if(i1 == 1){
			 if(ssa_tmp1 instanceof SSAInvokeInstruction){
				 if((((SSAInvokeInstruction) ssa_tmp1).getDeclaredTarget().getDeclaringClass().getName().toString().equals("Ljava/util/Iterator") &&
						 ((SSAInvokeInstruction) ssa_tmp1).getDeclaredTarget().getName().toString().equals("hasNext") &&
						 ((SSAInvokeInstruction) ssa_tmp1).getDeclaredTarget().getReturnType().getName().toString().equals("Z") &&
						 ((SSAInvokeInstruction) ssa_tmp1).isDispatch()) ||
						 (((SSAInvokeInstruction) ssa_tmp1).getDeclaredTarget().getDeclaringClass().getName().toString().contains("Iterator") &&
								 ((SSAInvokeInstruction) ssa_tmp1).getDeclaredTarget().getName().toString().equals("hasNext") &&
								 ((SSAInvokeInstruction) ssa_tmp1).getDeclaredTarget().getReturnType().getName().toString().equals("Z") &&
								 ((SSAInvokeInstruction) ssa_tmp1).isDispatch()) ){
					 System.out.println("i am here 1");
					 retVal1 = true;
				}
			}
		 }
		 

		 //System.out.println(ibb1.toString());
		 //System.out.println(cfg.getPredNodeCount(ibb1));
		 SSAInstruction ssa_tmp2 = null;
		 ISSABasicBlock ibb2 = null;
		 // BB3 empty
		 ibb2 = isCollectionImmediataPred(ibb1,cfg);
		 //System.out.println(ibb2.toString());
		 //System.out.println(retVal1);
		 if(retVal1 && ibb2 != null){
			 System.out.println("i am here 2");
			 retVal2 = true;
		 }
		 
		 //retInst = ssa_tmp1; //at this point, we can consider it as iterator
		 
		 // BB2 2 v5 = invokevirtual < Application, Ljava/util/HashSet, iterator()Ljava/util/Iterator; > v3 @4 exception:v4
		 //int i3 = 0;
		 //SSAInstruction ssa_tmp3 = null;
		 //ISSABasicBlock ibb3 = null;
		// System.out.println(cfg.getPredNodeCount(ibb2));
		// System.out.println(cfg.getSuccNodeCount(ibb2));anObject
		// if(ibb2 != null && cfg.getPredNodeCount(ibb2) == 1){
		//	 Collection<ISSABasicBlock> pred2 = cfg.getNormalPredecessors(ibb2);
		//	 for(ISSABasicBlock iiib : pred2){
		//		 ibb3 = iiib;
		//		// System.out.println("fxxxk");
		//		 for(SSAInstruction inst: iiib){
		//			 if(inst instanceof SSAInvokeInstruction){
		//				 if(inst instanceof SSAPhiInstruction || inst == null)
		//					 continue;
		//				 i3++;
		//				 ssa_tmp3 = inst;
		//			 }
		//		 }
		//	 }
		 //}
		 
		 SSAInstruction tmpInst  = null;
		 tmpInst = SSAUtil.getSSAByDU(cgNode,ssa_tmp1.getUse(0));
		// System.out.println(tmpInst.toString());
		 if(retVal2 && (tmpInst!=null)){
			 if(tmpInst instanceof SSAInvokeInstruction){
				 if(//((SSAInvokeInstruction) ssa_tmp3).getDeclaredTarget().getDeclaringClass().getName().toString().contains("Ljava/util") &&
						 ((SSAInvokeInstruction) tmpInst).getDeclaredTarget().getReturnType().getName().toString().equals("Ljava/util/Iterator") &&
						 ((SSAInvokeInstruction) tmpInst).isDispatch()){
					 System.out.println("i am here 3");
					 retVal3 = true;
				 }
				 else{
					System.out.println("user defined iterator");
					System.out.println(tmpInst.toString());
					retVal3 = true;
					retInst = tmpInst;
					return retInst;
				}
			}else{ // not function call 
				if(tmpInst instanceof SSANewInstruction){
					SSAInvokeInstruction callTmp = null;
					if((callTmp = SSAUtil.getInit4New(((SSANewInstruction)tmpInst).getNewSite().getDeclaredType().getName().toString(),cgNode,tmpInst.getDef(0))) != null){
						retVal3 = true;
						//tmpInst = callTmp;
						return callTmp;
					}else{
						System.out.println("corner case  1 in collection determination");
						System.out.println(tmpInst.toString());
						assert 0 == 1;
					}
				}else{
					System.out.println("corner case 2  in collection determination");
					System.out.println(tmpInst.toString());
					assert 0 == 1;
				}
			}
		 }
		
		 if(retVal2 && tmpInst == null){ //maybe parameter or constant 
			 retInst = ssa_tmp1;
			 return retInst;
		 }
		 
		 //System.out.println(ssa_tmp3.toString());
		 //System.out.println(i3);
		/* if(retVal2 && i3 == 1 && ibb3 != null){
			 if(ssa_tmp3 instanceof SSAInvokeInstruction){
				 //System.out.println(ssa_tmp3.toString());
				 //System.out.println(((SSAInvokeInstruction) ssa_tmp3).getDeclaredTarget().getDeclaringClass().getName().toString());
				 //System.out.println(((SSAInvokeInstruction) ssa_tmp3).getDeclaredTarget().getReturnType().getName().toString());
				 //System.out.println(((SSAInvokeInstruction) ssa_tmp3).isDispatch());
				 if(//((SSAInvokeInstruction) ssa_tmp3).getDeclaredTarget().getDeclaringClass().getName().toString().contains("Ljava/util") &&
						 ((SSAInvokeInstruction) ssa_tmp3).getDeclaredTarget().getReturnType().getName().toString().equals("Ljava/util/Iterator") &&
						 ((SSAInvokeInstruction) ssa_tmp3).isDispatch()){
					 System.out.println("i am here 3");
					 retVal3 = true;
				}else{
					System.out.println("user defined iterator");
					retVal3 = true;
					retInst = ssa_tmp3;
					return retInst;
				}
			}
		 }*/
		 //System.out.println(ibb3.toString());
		 // BB1 1 v3 = getfield < Application, LTestCase, mySet, <Application,Ljava/util/HashSet> > v1, this is for field collection 
		/* int i4 = 0;
		 SSAInstruction ssa_tmp4 = null;
		 ISSABasicBlock ibb4;
		 if(cfg.getPredNodeCount(ibb3) == 1){
			 Collection<ISSABasicBlock> pred3 = cfg.getNormalPredecessors(ibb3);
			 for(ISSABasicBlock iiib : pred3){
				 ibb4 = iiib;
				 for(SSAInstruction inst: iiib){
					 i4++;
					 ssa_tmp4 = inst;
				 }
			 }
		 }*/
		 
		 if(retVal3){
			 HashSet<MyTriple> moreRet = ModelLibrary.isUserDefinedIterator(((SSAInvokeInstruction)tmpInst),cg,looper,cgNode,txtPath,whoHasRun);
	
			 if(retVal3 && moreRet.size() == 0){			 
				 // as for field collection, this should be 
				 SSAInstruction fieldC = SSAUtil.getSSAByDU(cgNode,tmpInst.getUse(0));
				 if(fieldC != null && fieldC instanceof SSAFieldAccessInstruction){
					  System.out.println("field collection");
					// System.out.println(ssa_tmp4.toString());
					 retInst = fieldC;
				 }		 
				 // as for local collection, this should be a new instruction 
				 SSAInstruction localC = SSAUtil.getSSAByDU(cgNode,tmpInst.getUse(0));
				 if(localC != null && localC instanceof SSANewInstruction){
					 System.out.println("local collection");
					 retInst = localC;
				}
				 // from parameter 
				 if(SSAUtil.isVnParameter(cgNode,tmpInst.getUse(0))){
					 System.out.println("collection rely on parameter");
					 retInst = tmpInst;
				 }
				 
				 SSAInstruction tmpC = SSAUtil.getSSAByDU(cgNode,tmpInst.getUse(0));
				 if(retInst == null && tmpC != null){
					 System.out.println("maybe collection");
					 //assert 0 == 1;
					 retInst = tmpC;
				 }
				 
				 if(retInst == null && tmpC == null){
					 System.out.println("not collection");
					 retInst = null;
				 }	 
			 }
			 
			 if(retVal3 && moreRet.size() != 0){
				 userIter.addAll(moreRet);
				 System.out.println("user defined iterator");
				 retInst = null;
				 //assert 0 == 1;
			 }
		 }
		 return retInst;
	 }
	 
	 public static ISSABasicBlock isCollectionImmediataPred(ISSABasicBlock bb, SSACFG cfg){
		ISSABasicBlock retBB = null;		

		//ISSABasicBlock[] mybb = new ISSABasicBlock[2];
		//HashSet<ISSABasicBlock> mybb = new HashSet<ISSABasicBlock>();
		boolean flag = false;
		int i = 0;
//		System.out.println("sbbbbbbbbbbbbbbbbbbbbb" + cfg.getPredNodeCount(bb));
		
		//if(cfg.getPredNodeCount(bb) == 2){
			for(ISSABasicBlock ibb : cfg.getNormalPredecessors(bb)){
				//mybb.add(ibb);
				//mybb[i++] = ibb;
				if(isEmptyBB(ibb)){
					retBB = ibb;
				}
				if(isGotoBB(ibb)){
					flag = true;
				}
			}
			/*System.out.println(bb.toString());
			System.out.println(mybb[0].toString());
			System.out.println(mybb[1].toString());
			System.out.println(isEmptyBB(mybb[0]));
			System.out.println(isGotoBB(mybb[1]));
			System.out.println(isEmptyBB(mybb[1]));
			System.out.println(isGotoBB(mybb[0]));*/
			//if((isEmptyBB(mybb[0]) && isGotoBB(mybb[1])) || (isEmptyBB(mybb[1]) && isGotoBB(mybb[0])))
			//	retBB = isEmptyBB(mybb[0]) ? mybb[0] : mybb[1];
		//} 
		if(flag = true && retBB != null)
			return retBB;
		else
			return null;
	 }

	 public static boolean isEmptyBB(ISSABasicBlock bb){
		 int num = 0;
		 SSAInstruction target = null;
		 for(SSAInstruction tmp : bb){
			 num++;
			 target = tmp;
		 }
		 if(num == 0)
			 return true;
		 else
			 return false;
	 }
	 
	 public static boolean isGotoBB(ISSABasicBlock bb){
		 SSAInstruction retVal = onlyOneSSA(bb);
		 if(retVal != null && retVal instanceof SSAGotoInstruction)
			 return true;
		 return false;
	 }
	 
	 public static SSAInstruction onlyOneSSA(ISSABasicBlock bb){
		 int num = 0;
		 SSAInstruction target = null;
		 for(SSAInstruction tmp : bb){
			 num++;
			 target = tmp;
		 }
		 if(num == 1)
			 return target;
		 else
			 return null;
		 
	 }
	 
	 public static int containResult(HashSet<MyTuple> resultBuff, MyTuple tester){
		 for(MyTuple tmp:resultBuff){
			 if(tmp.getSSA().equals(tester.getSSA())){
				 if(tmp.getCGN().equals(tester.getCGN()))
					 if(tmp.getParaLoc() == tester.getParaLoc())
						 return tmp.getResult() == true ? 1:0;
			 }
		 }
		 return -1;
	 }
	  
	/* public static boolean isBounded(ProcessUnit unit, LoopAnalyzer looper,HashSet<MyTuple> resultBuff, HashSet<MyTriple> bufList,String txtPath, HashSet<MyPair> whoHasRun){
		 MyTuple tmpR = new MyTuple(unit.inst,unit.cgNode,true,unit.inIndex);
		 if(containResult(resultBuff,tmpR) == 0)
			 return false;
		 if(containResult(resultBuff,tmpR) == 1)
			 return true;
		 unit.run();
		 System.out.println("===============================================================");
		 if(unit.strongBounded){
			 MyTuple tmpR1 = new MyTuple(unit.inst,unit.cgNode,true,unit.inIndex);
			 if(containResult(resultBuff,tmpR1) == -1)
				 resultBuff.add(tmpR1);
			 System.out.println("it's strongly bounded");
			 System.out.println("class name" + ":" + unit.cgNode.getMethod().getDeclaringClass().getName().toString());
			 System.out.println("method name" + ":" + unit.cgNode.getMethod().getName().toString());
			 System.out.println("SSAInstruction" + ":" + unit.inst.toString());
			 System.out.println("line number" + ":" + IRUtil.getSourceLineNumberFromSSA(unit.cgNode,unit.inst));
			 System.out.println("level" + ":" + unit.level);
			 System.out.println("***********************************************");
			 return true;
		 }
		 if(unit.isBounded == false){
			 System.out.println("not bounded");
			 System.out.println("***********************************************");
			 System.out.println("class name" + ":" + unit.cgNode.getMethod().getDeclaringClass().getName().toString());
			 System.out.println("method name" + ":" + unit.cgNode.getMethod().getName().toString());
			 System.out.println("SSAInstruction" + ":" + unit.inst.toString());
			 System.out.println("line number" + ":" + IRUtil.getSourceLineNumberFromSSA(unit.cgNode,unit.inst));
			 System.out.println("level" + ":" + unit.level);
			 System.out.println("***********************************************");
			 MyTuple tmpR21 = new MyTuple(unit.inst,unit.cgNode,false,unit.inIndex);
			 if(containResult(resultBuff,tmpR21) == -1)
				 resultBuff.add(tmpR21);
			 return false;
		 }
		 if(unit.level > 10000){
			 System.out.println("exceed the threshold");
			 System.out.println("***********************************************");
			 System.out.println("class name" + ":" + unit.cgNode.getMethod().getDeclaringClass().getName().toString());
			 System.out.println("method name" + ":" + unit.cgNode.getMethod().getName().toString());
			 System.out.println("SSAInstruction" + ":" + unit.inst.toString());
			 System.out.println("line number" + ":" + IRUtil.getSourceLineNumberFromSSA(unit.cgNode,unit.inst));
			 System.out.println("level" + ":" + unit.level);
			 System.out.println("***********************************************");
			 MyTuple tmpR2 = new MyTuple(unit.inst,unit.cgNode,false,unit.inIndex);
			 if(containResult(resultBuff,tmpR2) == -1)
				 resultBuff.add(tmpR2);
			 return false;
		 }
		 LinkedList<ProcessUnit> work_set = new LinkedList<ProcessUnit>();
		 HashSet<MyTriple> newlyUnit = unit.newlyCGN;
		 unit.printNewlyCGN();
		 if(newlyUnit.size() == 0){
			 MyTuple tmpR4 = new MyTuple(unit.inst,unit.cgNode,unit.specailBounded,unit.inIndex);
			 if(containResult(resultBuff,tmpR4) == -1)
				 resultBuff.add(tmpR4);			 
			 return unit.specailBounded;
		 }
		 int next_level = ++unit.level;
		 for(MyTriple entry : newlyUnit){	 
			 boolean isBranch = false;
			 SSAInstruction ssa_tmp = entry.getSSA();
			 int int_tmp = entry.getParaLoc();
			 CGNode cgn_tmp = entry.getCGNode();
			 if(ssa_tmp instanceof SSAConditionalBranchInstruction)
				 isBranch = true;
			 //if(!SSAUtil.isAlreadyIn(entry.getKey(),bufList)){
			 if(!isAlreadyInTriple(entry,bufList)){	 
				 System.out.println("???????????put into process unit??????????" + ssa_tmp.toString());
				 ProcessUnit new_unit = new ProcessUnit(unit.cg,cgn_tmp,ssa_tmp,isBranch,next_level,int_tmp,looper,txtPath,entry.optChain,whoHasRun);
				 work_set.add(new_unit);
				 //bufList.add(entry.getKey());
				 bufList.add(entry);
			 }else{// form a circle 
				 System.out.println("form a circle...");
			 }
		}
		if(work_set.size() == 0){ // need revisit it
			 MyTuple tmpR3 = new MyTuple(unit.inst,unit.cgNode,true,unit.inIndex);
			 if(containResult(resultBuff,tmpR3) == -1)
				 resultBuff.add(tmpR3);
			return true;
		}
		else{
			boolean retVal = true;
			for(ProcessUnit x : work_set){
				retVal &= isBounded(x,looper,resultBuff,bufList,txtPath);
			}
			 MyTuple tmpR4 = new MyTuple(unit.inst,unit.cgNode,retVal,unit.inIndex);
			 if(containResult(resultBuff,tmpR4) == -1)
				 resultBuff.add(tmpR4);
			return retVal;
		}
	}*/
	 
	 public static void markHasChild(CGNode cgN, SSAInstruction ssa, int para_loc, HashSet<MyTriple> outerList){
		 for(MyTriple tmp : outerList){
			 if(tmp.getCGNode().equals(cgN)){
				 if(tmp.getSSA().equals(ssa)){
					 if(tmp.getParaLoc() == para_loc){
						 tmp.setHasChild(false);
						 return;
					 }
				 }
			 }
		 }
		 
	 }
	 
	 public static void markSpecialBound(CGNode cgN, SSAInstruction ssa, int para_loc, HashSet<MyTriple> outerList){
		 for(MyTriple tmp : outerList){
			 if(tmp.getCGNode().equals(cgN)){
				 if(tmp.getSSA().equals(ssa)){
					 if(tmp.getParaLoc() == para_loc){
						 tmp.setBounded(true);
						 return;
					 }
				 }
			 }
		 }
		 
	 }
	 
	 
	 public static void markLibInvolved(CGNode cgN, SSAInstruction ssa, int para_loc, HashSet<MyTriple> outerList){
		 for(MyTriple tmp : outerList){
			 if(tmp.getCGNode().equals(cgN)){
				 if(tmp.getSSA().equals(ssa)){	
						//		10 v7 = binaryop(mul) v2 , v5:#100
						//		11 v8 = binaryop(add) v4:#1 , v7
						//		12 conditional branch(ge, to iindex=21) v13,v8
						//get 11 v8 = binaryop(add) v4:#1 , v7 for this conditional branch 
						// v8 should have def 
					/*	public SSAInstruction getImmediateSSA(){
							return SSAUtil.getSSAIndexByDefvn(this.cgNode.getIR().getInstructions(), this.valNum);
						}
						
						public void initialBWS(){
							this.ssaInst = getImmediateSSA();
							System.out.println(this.ssaInst.toString());
							this.bHelper = new BackwardSlicing(this.ssaInst,this.cgNode);
							System.out.println("finish initializing bws1");
							this.analyzedSSA = this.bHelper.getDataSlicing(this.ssaInst);
							System.out.println("finish initializing bws2");
						}
						
						public void printAnalyzedSSA(){
							System.out.println("print analyzed ssa");
							this.bHelper.printSlice(this.analyzedSSA);
						}*/
					 if(tmp.getParaLoc() == para_loc){
						 tmp.setLibraryInvovled();
						 return;
					 }
				 }
			 }
		 }
		 
	 }
	 
	 public static void markNotFoundCaller(CGNode cgN, SSAInstruction ssa, int para_loc, HashSet<MyTriple> outerList){
		 for(MyTriple tmp : outerList){
			 if(tmp.getCGNode().equals(cgN)){
				 if(tmp.getSSA().equals(ssa)){
					 if(tmp.getParaLoc() == para_loc){
						 tmp.setNotFoundCaller(true);
						 return;
					 }
				 }
			 }
		 } 
	 }
	 
	 
	 public static void makeCollectionOrArrayBounded(CGNode cgN, SSAInstruction ssa, int para_loc, HashSet<MyTriple> outerList){
		 for(MyTriple tmp : outerList){
			 if(tmp.getCGNode().equals(cgN)){
				 if(tmp.getSSA().equals(ssa)){
					 if(tmp.getParaLoc() == para_loc){
						 tmp.setCABounded();
						 return;
					 }
				 }
			 }
		 } 
	 }
	 
	 public static void addConf(CGNode cgN, SSAInstruction ssa, int para_loc, HashSet<MyTriple> outerList, HashSet<String> conf){
		 for(MyTriple tmp : outerList){
			 if(tmp.getCGNode().equals(cgN)){
				 if(tmp.getSSA().equals(ssa)){
					 if(tmp.getParaLoc() == para_loc){
						 tmp.addConfVar(conf);
						 return;
					 }
				 }
			 }
		 }
	 }
	 
	 
	 public static void addConstVar(CGNode cgN, SSAInstruction ssa, int para_loc, HashSet<MyTriple> outerList, HashSet<String> constVar){
		 for(MyTriple tmp : outerList){
			 if(tmp.getCGNode().equals(cgN)){
				 if(tmp.getSSA().equals(ssa)){
					 if(tmp.getParaLoc() == para_loc){
						 tmp.addConstantVar(constVar);
						 return;
					 }
				 }
			 }
		 }
	 }
	 
	 public static void addInfo(CGNode cgN, SSAInstruction ssa, int para_loc, HashSet<MyTriple> outerList, LinkedList<String> inInfo){
		 for(MyTriple tmp : outerList){
			 if(tmp.getCGNode().equals(cgN)){
				 if(tmp.getSSA().equals(ssa)){
					 if(tmp.getParaLoc() == para_loc){
						 tmp.addInfo(inInfo);
						 return;
					 }
				 }
			 }
		 }
	 }
	 
	 public static MyTriple findMyTriple(CGNode cgN, SSAInstruction ssa, int para_loc, HashSet<MyTriple> bufList){
		 for(MyTriple tmp : bufList){
			 if(tmp.getCGNode().equals(cgN)){
				 if(tmp.getSSA().equals(ssa)){
					 if(tmp.getParaLoc() == para_loc){
						 return tmp;
					 }
				 }
			 }
		 }
		 return null;
	 }
	 
	 
	 
	 public static void isBoundedSearch(ProcessUnit unit, LoopAnalyzer looper,HashSet<MyTriple> bufList,String txtPath, HashSet<MyPair> whoHasRun){
		 unit.run();
		 
		 MyTriple forUnit = new MyTriple(unit.inst,unit.cgNode,unit.inIndex);
		 forUnit.setHasParent();
		 bufList.add(forUnit);
		
		 
		 LinkedList<ProcessUnit> work_set = new LinkedList<ProcessUnit>();
		 HashSet<MyTriple> newlyUnit = unit.newlyCGN;
		 unit.printNewlyCGN();
		 int next_level = ++unit.level;
		 if(newlyUnit.size() == 0){
			 markHasChild(unit.cgNode,unit.inst,unit.inIndex,bufList);
		 }
		 if(unit.strongBounded){
			 markSpecialBound(unit.cgNode,unit.inst,unit.inIndex,bufList);
		 }
		 
		 if(unit.notFindCaller){
			 markNotFoundCaller(unit.cgNode,unit.inst,unit.inIndex,bufList);
		 }
		 if(unit.info.size() != 0)
			 addInfo(unit.cgNode,unit.inst,unit.inIndex,bufList,unit.info);
		 if(unit.relatedConf.size() != 0){
			 addConf(unit.cgNode,unit.inst,unit.inIndex,bufList,unit.relatedConf);
		 }
		 if(unit.constVar.size() != 0){
			 addConstVar(unit.cgNode,unit.inst,unit.inIndex,bufList,unit.constVar);
		 }
		 if(unit.isLibraryInvovled){
			 markLibInvolved(unit.cgNode,unit.inst,unit.inIndex,bufList);
		 }
		 if(unit.collectionIsBounded){
			 makeCollectionOrArrayBounded(unit.cgNode,unit.inst,unit.inIndex,bufList);
		 }
		 for(MyTriple entry : newlyUnit){	 
			 boolean isBranch = false;
			 SSAInstruction ssa_tmp = entry.getSSA();
			 int int_tmp = entry.getParaLoc();
			 CGNode cgn_tmp = entry.getCGNode();
			 entry.setParent(forUnit);
			 entry.info.addAll(forUnit.info);
			 if(int_tmp == -2 || int_tmp == -3 || int_tmp == -4 || int_tmp == -5 || int_tmp == -6 || int_tmp == -7 || int_tmp == -8){ // while true case 
				if(!isAlreadyInTriple(entry,bufList))
					bufList.add(entry);
				continue; 
			 }
			 if(ssa_tmp instanceof SSAConditionalBranchInstruction)
				 isBranch = true;
			 //if(!SSAUtil.isAlreadyIn(entry.getKey(),bufList)){
			 if(!isAlreadyInTriple(entry,bufList)){	 
				 System.out.println("???????????put into process unit??????????" + ssa_tmp.toString());
				 if(unit.opt.size() != 0 && entry.optChain == null)
					 entry.optChain = unit.optObj;
				 ProcessUnit new_unit = new ProcessUnit(unit.cg,cgn_tmp,ssa_tmp,isBranch,next_level,int_tmp,looper,txtPath,entry.optChain,whoHasRun);
				 if(entry.retSourceFlag)
					 new_unit.setRetSource(true,entry.retSource);
				 work_set.add(new_unit);
				 //bufList.add(entry.getKey());				 
				 bufList.add(entry);
			 }else{// form a circle 
				 System.out.println("form a circle...");
			 }
		}
		 
		 
		while(!work_set.isEmpty()){
			System.out.println("working in isBoundedSearch");
			ProcessUnit tmp = work_set.poll();
			tmp.run();
			tmp.printNewlyCGN();
			MyTriple parent = findMyTriple(tmp.cgNode,tmp.inst,tmp.inIndex,bufList);
			assert parent != null;
			HashSet<MyTriple> tmpUnit = tmp.newlyCGN;
			
			if(tmpUnit.size() == 0){
				markHasChild(tmp.cgNode,tmp.inst,tmp.inIndex,bufList);
			}
			
			if(tmp.strongBounded){
				markSpecialBound(tmp.cgNode,tmp.inst,tmp.inIndex,bufList);
			}
			
			if(tmp.info.size() != 0){
				 addInfo(tmp.cgNode,tmp.inst,tmp.inIndex,bufList,tmp.info);
			}
			
			 if(tmp.notFindCaller){
				 markNotFoundCaller(tmp.cgNode,tmp.inst,tmp.inIndex,bufList);
			 }
			
			 if(tmp.relatedConf.size() != 0){
				 addConf(tmp.cgNode,tmp.inst,tmp.inIndex,bufList,tmp.relatedConf);
			 }
			 
			 if(tmp.constVar.size() != 0){
			 	 addConstVar(tmp.cgNode,tmp.inst,tmp.inIndex,bufList,tmp.constVar);
			 }
			 
			 if(tmp.isLibraryInvovled){
				 markLibInvolved(tmp.cgNode,tmp.inst,tmp.inIndex,bufList);
			 }
			 
			 if(tmp.collectionIsBounded){
				 makeCollectionOrArrayBounded(tmp.cgNode,tmp.inst,tmp.inIndex,bufList);
			 }
			 
			for(MyTriple entry : tmpUnit){	 
				boolean isBranch = false;
				SSAInstruction ssa_tmp = entry.getSSA();
				int int_tmp = entry.getParaLoc();
				CGNode cgn_tmp = entry.getCGNode();
				entry.setParent(parent);
				entry.info.addAll(parent.info);
				if(int_tmp == -2 || int_tmp == -3 || int_tmp == -4 || int_tmp == -5 || int_tmp == -6 || int_tmp == -7 || int_tmp == -8){
					if(!isAlreadyInTriple(entry,bufList)){
						bufList.add(entry);
					}
					continue;
				}
				
				if(ssa_tmp instanceof SSAConditionalBranchInstruction)
					isBranch = true;
				//if(!SSAUtil.isAlreadyIn(entry.getKey(),bufList)){
				if(!isAlreadyInTriple(entry,bufList)){	 
					System.out.println("???????????put into process unit??????????" + ssa_tmp.toString());
					next_level = ++tmp.level;
					if(tmp.opt.size() != 0 && entry.optChain == null)
						entry.optChain = tmp.optObj;
					ProcessUnit new_unit = new ProcessUnit(unit.cg,cgn_tmp,ssa_tmp,isBranch,next_level,int_tmp,looper,txtPath,entry.optChain,whoHasRun);
					work_set.add(new_unit);
					if(entry.retSourceFlag)
						new_unit.setRetSource(true,entry.retSource);
					//bufList.add(entry.getKey());
					bufList.add(entry);
				}else{// form a circle 
					System.out.println("form a circle...");
				}
			}
		} 		
	}
	 
	 
	// covert hashset to list of labelled ssa  
	public static LinkedList<LabelledSSA> set2LabelledList(HashSet<LabelledSSA> mySet,int level){
		LinkedList<LabelledSSA> ret = new LinkedList<LabelledSSA>();
		for(LabelledSSA tmp : mySet){
			SSAInstruction key = tmp.getSSA();
			CGNode value = tmp.getCGNode();
			LabelledSSA newLabelledSSA = new LabelledSSA(key,value,level);
			ret.add(newLabelledSSA);
		}
		return ret;
	} 
	
	public static SSAInstruction isArrayLengthInst(int varNum,CGNode cgNode){
		//SSAInstruction tmp = SSAUtil.getSSAIndexByDefvn(ssas, varNum);
		SSAInstruction tmp = SSAUtil.getSSAByDU(cgNode, varNum); 
		//System.out.println(tmp.toString());
		if(tmp != null){
			if(tmp instanceof SSAArrayLengthInstruction)
				return tmp;
		}
		return tmp;
	}
	
	public static boolean isCollectionMethod(SSAInstruction ssa){
		boolean retVal = false;
		if(ssa instanceof SSAInvokeInstruction){
			if(((SSAInvokeInstruction) ssa).getDeclaredTarget().getDeclaringClass().getName().toString().matches("Ljava/util/[a-zA-Z]*Set") ||
			((SSAInvokeInstruction) ssa).getDeclaredTarget().getDeclaringClass().getName().toString().matches("Ljava/util/[a-zA-Z]*List") ||
			((SSAInvokeInstruction) ssa).getDeclaredTarget().getDeclaringClass().getName().toString().matches("Ljava/util/[a-zA-Z]*Map") ||
			((SSAInvokeInstruction) ssa).getDeclaredTarget().getDeclaringClass().getName().toString().matches("Ljava/util/[a-zA-Z]*Collection")||
			((SSAInvokeInstruction) ssa).getDeclaredTarget().getDeclaringClass().getName().toString().equals("Ljava/util/Map$Entry") ||
			((SSAInvokeInstruction) ssa).getDeclaredTarget().getDeclaringClass().getName().toString().equals("Ljava/util/concurrent/ConcurrentNavigableMap") ||
			((SSAInvokeInstruction) ssa).getDeclaredTarget().getDeclaringClass().getName().toString().equals("Lorg/apache/cassandra/utils/BiMultiValMap") ||
			((SSAInvokeInstruction) ssa).getDeclaredTarget().getDeclaringClass().getName().toString().equals("Lcom/google/common/collect/Multimap") ||
			((SSAInvokeInstruction) ssa).getDeclaredTarget().getDeclaringClass().getName().toString().equals("Lcom/google/common/collect/HashMultimap") 
			//((SSAInvokeInstruction) ssa).getDeclaredTarget().getDeclaringClass().getName().toString().equals("Lorg/apache/cassandra/io/sstable/SSTableScanner") 

			){
				if(((SSAInvokeInstruction) ssa).getDeclaredTarget().getName().toString().equals("size") ||
						((SSAInvokeInstruction) ssa).getDeclaredTarget().getName().toString().equals("put") ||
						((SSAInvokeInstruction) ssa).getDeclaredTarget().getName().toString().equals("putAll") ||
						((SSAInvokeInstruction) ssa).getDeclaredTarget().getName().toString().equals("add") ||
						((SSAInvokeInstruction) ssa).getDeclaredTarget().getName().toString().equals("addAll") ||
						((SSAInvokeInstruction) ssa).getDeclaredTarget().getName().toString().contains("put") ||
						((SSAInvokeInstruction) ssa).getDeclaredTarget().getName().toString().contains("putAll") ||
						((SSAInvokeInstruction) ssa).getDeclaredTarget().getName().toString().contains("add") ||
						((SSAInvokeInstruction) ssa).getDeclaredTarget().getName().toString().contains("addAll") ||
						((SSAInvokeInstruction) ssa).getDeclaredTarget().getName().toString().contains("<init>") ||
						((SSAInvokeInstruction) ssa).getDeclaredTarget().getName().toString().contains("values") ||
						((SSAInvokeInstruction) ssa).getDeclaredTarget().getName().toString().contains("entrySet") ||
						((SSAInvokeInstruction) ssa).getDeclaredTarget().getName().toString().contains("keySet") ||
						((SSAInvokeInstruction) ssa).getDeclaredTarget().getName().toString().contains("getValue") ||
						((SSAInvokeInstruction) ssa).getDeclaredTarget().getName().toString().contains("getKey") ||
						((SSAInvokeInstruction) ssa).getDeclaredTarget().getName().toString().contains("hasNext") ||
						((SSAInvokeInstruction) ssa).getDeclaredTarget().getName().toString().contains("iterator") 
						//((SSAInvokeInstruction) ssa).getDeclaredTarget().getName().toString().contains("create")
						)
					retVal = true;
			}
		}
		return retVal;
	}
	

	public static boolean isCollectionSizeMethod(SSAInstruction ssa){
		boolean retVal = false;
		if(ssa instanceof SSAInvokeInstruction){
			if(((SSAInvokeInstruction) ssa).getDeclaredTarget().getDeclaringClass().getName().toString().matches("Ljava/util/[a-zA-Z]*Set") ||
			((SSAInvokeInstruction) ssa).getDeclaredTarget().getDeclaringClass().getName().toString().matches("Ljava/util/[a-zA-Z]*List") ||
			((SSAInvokeInstruction) ssa).getDeclaredTarget().getDeclaringClass().getName().toString().matches("Ljava/util/[a-zA-Z]*Map") ||
			((SSAInvokeInstruction) ssa).getDeclaredTarget().getDeclaringClass().getName().toString().matches("Ljava/util/[a-zA-Z]*Collection") ||
			((SSAInvokeInstruction) ssa).getDeclaredTarget().getDeclaringClass().getName().toString().equals("Ljava/util/Map$Entry") ||
			((SSAInvokeInstruction) ssa).getDeclaredTarget().getDeclaringClass().getName().toString().equals("Ljava/util/concurrent/ConcurrentNavigableMap") ||
			((SSAInvokeInstruction) ssa).getDeclaredTarget().getDeclaringClass().getName().toString().equals("Lorg/apache/cassandra/utils/BiMultiValMap") ||
			((SSAInvokeInstruction) ssa).getDeclaredTarget().getDeclaringClass().getName().toString().equals("Lcom/google/common/collect/Multimap") ||
			((SSAInvokeInstruction) ssa).getDeclaredTarget().getDeclaringClass().getName().toString().equals("Lcom/google/common/collect/HashMultimap") 

			){
				if(((SSAInvokeInstruction) ssa).getDeclaredTarget().getName().toString().equals("size"))
					retVal = true;
			}
		}
		return retVal;
	}
	
	// for case like collection<String> x = new ArrayList<>(y), y is collection
	// then we need continue analyzing y 
	public static HashSet<Integer> isCollectionMethodInit(SSAInstruction ssa, CGNode cgNode){
	//	System.out.println("isCollectionMethodInit");
	//	System.out.println(ssa.toString());
	//	System.out.println(((SSANewInstruction) ssa).getNewSite().getDeclaredType().getName().toString());
		HashSet<Integer> ret = new HashSet<Integer>();
		if(ssa instanceof SSANewInstruction){
			String initial = ((SSANewInstruction) ssa).getNewSite().getDeclaredType().getName().toString();
			SSACFG cfg = cgNode.getIR().getControlFlowGraph();
		    for (Iterator<ISSABasicBlock> ibb = cfg.iterator(); ibb.hasNext(); ) {
		 	      ISSABasicBlock bb = ibb.next();
		 	      for (Iterator<SSAInstruction> issa = bb.iterator(); issa.hasNext(); ) {
		 	    	  SSAInstruction tmp_ssa = issa.next();	
		 	    	  if(tmp_ssa instanceof SSAInvokeInstruction){  
		 	    		  if(((SSAInvokeInstruction) tmp_ssa).getDeclaredTarget().getName().toString().contains("<init>")){
		 	    			  if(tmp_ssa.getUse(0) == ssa.getDef(0)){
		 	    				  if(((SSAInvokeInstruction) tmp_ssa).getDeclaredTarget().getSelector().toString().contains("<init>(Ljava/util/")){
		 	    					//  System.out.println(tmp_ssa.toString());
		 	    					 // System.out.println((((SSAInvokeInstruction) tmp_ssa).getDeclaredTarget().getName().toString().contains(initial)));
		 	    					  ret.add(tmp_ssa.getUse(1));
		 	    				  }
		 	    			  }
		 	    		  }
		 	    	  }
		 	      }
		  }
		}
		return ret;
	}
	
	
	public static boolean newCollection(SSAInstruction ssa){
		boolean retVal = false;
		if(ssa instanceof SSANewInstruction){
			if(((SSANewInstruction) ssa).getNewSite().getDeclaredType().getName().toString().matches("Ljava/util/[a-zA-Z]*Set") ||
			   ((SSANewInstruction) ssa).getNewSite().getDeclaredType().getName().toString().matches("Ljava/util/[a-zA-Z]*List")  ||
			   ((SSANewInstruction) ssa).getNewSite().getDeclaredType().getName().toString().matches("Ljava/util/[a-zA-Z]*Map") ||
			   ((SSANewInstruction) ssa).getNewSite().getDeclaredType().getName().toString().matches("Ljava/util/[a-zA-Z]*Collection")
				){
				retVal = true;
			}	
		}
		return retVal;
	}
	
	public static void putNewlyCGN(HashSet<MyTriple> newlyCGN, HashSet<MyTriple> candidate){
		for(MyTriple entry : candidate){
			if(!SSAUtil.isAlreadyInTriple(entry, newlyCGN)){
				newlyCGN.add(entry);
			}
		}
	}
	
	//relocate the loop condition location for collection inst
	public static ISSABasicBlock ifHasNextBB(ISSABasicBlock bb,SSACFG cfg){
		ISSABasicBlock retBB = null;
		SSAInstruction targetInst = null;
		int num = 0;
		for(SSAInstruction tmp : bb){
			targetInst = tmp;
			++num;
		}
		if(num == 1){
			if(targetInst != null){
				 if(targetInst instanceof SSAInvokeInstruction){
					 if(((SSAInvokeInstruction) targetInst).getDeclaredTarget().getDeclaringClass().getName().toString().equals("Ljava/util/Iterator") &&
							 ((SSAInvokeInstruction) targetInst).getDeclaredTarget().getName().toString().equals("hasNext") &&
							 ((SSAInvokeInstruction) targetInst).getDeclaredTarget().getReturnType().getName().toString().equals("Z") &&
							 ((SSAInvokeInstruction) targetInst).isDispatch()){
							 for(ISSABasicBlock tmp : cfg.getNormalSuccessors(bb)){
								 retBB = tmp;
						 }
					 }
				}
			}
		}
		return retBB;
	}
	
	public static boolean isAlreadyLabelled(LabelledSSA ssa, Collection<LabelledSSA> target){
		for(LabelledSSA tmp : target){
			if(tmp.getCGNode().equals(ssa.getCGNode()))
				if(tmp.getSSA().equals(ssa.getSSA()))
					return true;
		}
		return false;
	}
	
	public static boolean isAlreadyInTriple(MyTriple ssa, HashSet<MyTriple> target){
		for(MyTriple tmp : target){
			if(tmp.getCGNode().equals(ssa.getCGNode()))
				if(tmp.getSSA().equals(ssa.getSSA()))
					if(tmp.getParaLoc() == ssa.getParaLoc())
						return true;
		}
		return false;
	}
	
	public static int paraLocTrans(SSAInstruction ssa, CGNode cgN, int para_loc){
		if(para_loc == -1 || para_loc == -2 || para_loc == -3 || para_loc == -4 || para_loc == -5 || para_loc == -6 || para_loc == -7 || para_loc == -8){
			return para_loc;
		}else{
			if(ssa instanceof SSAInvokeInstruction){
				if(((SSAInvokeInstruction)ssa).isStatic())
					return para_loc+1;
				else
					return para_loc;
			}
		}
		assert 0 == 1;
		return para_loc;
	}
	
	public static List<LabelledSSA> getSSAVariable(String ssaPath, CallGraph cg){
		String[] splitPath = ssaPath.split("-");
		assert splitPath.length == 2;
		/*for(String partStr: splitPath)
			System.out.println(partStr);*/
		String methPath = splitPath[0];
		int loopLine = Integer.parseInt(splitPath[1]);
		IR ir = null;
		IMethod im = null;
		List<LabelledSSA> retSSA = new ArrayList<LabelledSSA>();
    	for (Iterator<? extends CGNode> it = cg.iterator(); it.hasNext(); ) {
    		CGNode f = it.next();
	      	if ( LoopVarUtil.isApplicationMethod(f) ) {
	      		if ( !LoopVarUtil.isNativeMethod(f)){
	      			im = f.getMethod();
	      			if(im.getSignature().indexOf(methPath)>=0){
	      				ir = f.getIR();
	      				//For test, generate CFG 
	      				//System.out.println(f.getIR().toString());
	      			    SSACFG cfg = ir.getControlFlowGraph();
	      			    for (Iterator<ISSABasicBlock> cfg_it = cfg.iterator(); cfg_it.hasNext(); ) {
	      			    	ISSABasicBlock bb = cfg_it.next();
	      			    	for(Iterator<SSAInstruction> bb_it = bb.iterator(); bb_it.hasNext();){
	      			    		SSAInstruction ssaInst = bb_it.next();
	      			    		int lineOfSSA = 0;
	      			    		lineOfSSA = LoopVarUtil.getSourceLineNumberFromSSA(ssaInst, ir);
	      			    		//assert lineOfSSA != -1;
	      			    		if(lineOfSSA == loopLine){	    			
	      			    			//System.out.println("bingo:" + ssaInst.toString());
	      			    			//System.out.println(SSAUtil.fieldOfWhichClass((SSAFieldAccessInstruction)ssaInst));	      			    			SSAUtil.fieldOfWhichClass((SSAFieldAccessInstruction)ssaInst);
	      			    			//System.out.println(SSAUtil.fieldOfWhichVar((SSAFieldAccessInstruction)ssaInst));
	      			    			LabelledSSA myLabel = new LabelledSSA(ssaInst,f);
	      			    			retSSA.add(myLabel);
	      			    			
	      			    		}
	      			    	}
	      			    }
	      			}
	      		}
	      	}
    	}
		return retSSA;
	}
	
	// get the real call site from slicing result
	public static SSAInstruction getRealCallSiteFromSlicing(HashSet<SSAInstruction> slicer, String funcName, String className, int paraNum, String returnType){
		for(SSAInstruction tmp : slicer){
			if(tmp instanceof SSAInvokeInstruction){
				if(((SSAInvokeInstruction)tmp).getDeclaredTarget().getName().toString().equals(funcName) && 
				((SSAInvokeInstruction)tmp).getDeclaredTarget().getDeclaringClass().getName().toString().equals(className) &&
				((SSAInvokeInstruction)tmp).getDeclaredTarget().getReturnType().toString().equals(returnType) &&
				((SSAInvokeInstruction)tmp).getDeclaredTarget().getNumberOfParameters() == paraNum )
					return tmp;
			}
		}
		return null;
	}
	
	public static SSAInvokeInstruction getInit4New(String typeName, CGNode cgN, int def){
		SSACFG cfg = cgN.getIR().getControlFlowGraph();
 	    for (Iterator<ISSABasicBlock> ibb = cfg.iterator(); ibb.hasNext(); ) {
 	      ISSABasicBlock bb = ibb.next();
 	      for (Iterator<SSAInstruction> issa = bb.iterator(); issa.hasNext(); ) {
 	    	  SSAInstruction tmp_ssa = issa.next();
 	    	  if(tmp_ssa instanceof SSAInvokeInstruction){
 	    		  if(((SSAInvokeInstruction) tmp_ssa).getDeclaredTarget().getDeclaringClass().getName().toString().equals(typeName) &&
 	    				 ((SSAInvokeInstruction) tmp_ssa).getDeclaredTarget().getName().toString().equals("<init>") &&
 	    				  tmp_ssa.getUse(0) == def){
 	    			 return (SSAInvokeInstruction) tmp_ssa;
 	    		  }
 	    	  }
 	      }
 	    }
 	    return null;
 	}
	
	public static int getArgument(CGNode cgn, int paraloc){
		if(cgn.getMethod().isStatic())
			return paraloc;
		else
			return paraloc + 1;
	}
	
	public static void markConstant(CGNode cgNode, SSAInstruction ssa_tmp, int i, HashSet<String> constVar, boolean directIndex){
		if(ssa_tmp.getNumberOfUses() == 0){
			if(ssa_tmp instanceof SSANewInstruction){
				if(SSAUtil.newCollection(ssa_tmp)){
					constVar.add("new collection instruction");
				}
			}
			return;
		}
		
		int m;
		if(directIndex)
			m = i;
		else
			m = ssa_tmp.getUse(i);
		String[] varName = SSAUtil.getConstantVarName(cgNode,ssa_tmp,m);
		String varInfo = new String();
		if(ssa_tmp instanceof SSAConditionalBranchInstruction)
			varInfo += "branch";
		if(varName == null)
			varInfo += " IR";
		else
			varInfo += varName[0];
		varInfo += ":" +SSAUtil.getConstant(cgNode,m);
		constVar.add(varInfo);
	}
	
	
	public static void markConf(CGNode cgNode, SSAInstruction ssa_tmp, int i, HashSet<String> relatedConf, SymbolTable sT, String otherInfo){
		if(i == -1){
			relatedConf.add(otherInfo + " need have a look for this conf");
			return;
		}
		String[] varName = SSAUtil.getConstantVarName(cgNode,ssa_tmp,ssa_tmp.getUse(i));
		if(varName == null)	 
			relatedConf.add(otherInfo + "IR" + ":" + sT.getValueString(ssa_tmp.getUse(i)));
		else
			relatedConf.add(otherInfo + varName[0] + ":" + sT.getValueString(ssa_tmp.getUse(i)));
	}
	
	public static boolean userDefinedBounded(SSAInstruction ssa, CGNode cgNode, HashSet<String> relatedConf){
		boolean retVal = false;
		if(ssa instanceof SSANewInstruction){
			//System.out.println("newwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwww");
			//System.out.println(ssa.toString());
			//System.out.println(((SSANewInstruction) ssa).getNewSite().getDeclaredType().getName().toString());
			/*if(((SSANewInstruction) ssa).getNewSite().getDeclaredType().getName().toString().equals("Lorg/apache/hadoop/conf/Configuration")){
				retVal = true;
				assert 0 == 1;
				System.out.println("xxxxxxxxxxxxxxxxxnewxxxxxxxxxxxxxxxxxxxxxxxxx");
				System.out.println("class name" + ":" + this.cgNode.getMethod().getDeclaringClass().getName().toString());
				System.out.println("method name" + ":" + this.cgNode.getMethod().getName().toString());
				System.out.println("SSAInstruction" + ":" + ssa.toString());
				System.out.println("line number" + ":" + IRUtil.getSourceLineNumberFromSSA(this.cgNode,ssa));
				System.out.println("xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");
			}*/
		}
		if(ssa instanceof SSAInvokeInstruction){
			if(((SSAInvokeInstruction)ssa).getDeclaredTarget().getDeclaringClass().getName().toString().equals("Lorg/apache/hadoop/conf/Configuration")){
				retVal = true;
				System.out.println("xxxxxxxxxxxxxxxxxxcallxxxxxxxxxxxxxxxxxxxxxxx");
				System.out.println("class name" + ":" + cgNode.getMethod().getDeclaringClass().getName().toString());
				System.out.println("method name" + ":" + cgNode.getMethod().getName().toString());
				System.out.println("SSAInstruction" + ":" + ssa.toString());
				System.out.println("line number" + ":" + IRUtil.getSourceLineNumberFromSSA(cgNode,ssa));
				SymbolTable sT = cgNode.getIR().getSymbolTable();
				int confNum = 0;
				for(int i = 0; i < ssa.getNumberOfUses();i++){
					if(sT.isStringConstant(ssa.getUse(i))){
						System.out.println("string constant for configuration" + ":" + sT.getValueString(ssa.getUse(i)));
						SSAUtil.markConf(cgNode, ssa, i, relatedConf, sT, "configuration");
						confNum++;
					}
				}
				if(confNum == 0){
					SSAUtil.markConf(cgNode, ssa, -1, relatedConf, null, "configuration");
				}
				System.out.println("xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");
			}
			if(((SSAInvokeInstruction)ssa).getDeclaredTarget().getDeclaringClass().getName().toString().equals("Ljava/lang/System")){
				retVal = true;
				System.out.println("xxxxxxxxxxxxxxxxxxcallxxxxxxxxxxxxxxxxxxxxxxx");
				System.out.println("class name" + ":" + cgNode.getMethod().getDeclaringClass().getName().toString());
				System.out.println("method name" + ":" + cgNode.getMethod().getName().toString());
				System.out.println("SSAInstruction" + ":" + ssa.toString());
				//System.out.println("line number" + ":" + IRUtil.getSourceLineNumberFromSSA(this.cgNode,ssa));
				int sysNum = 0;
				SymbolTable sT = cgNode.getIR().getSymbolTable();
				for(int i = 0; i < ssa.getNumberOfUses();i++){
					if(sT.isStringConstant(ssa.getUse(i))){
						System.out.println("string constant for system call" + ":" + sT.getValueString(ssa.getUse(i)));
						SSAUtil.markConf(cgNode, ssa, i, relatedConf, sT, "system call");
						sysNum++;
					}
				}
				if(sysNum == 0){
					SSAUtil.markConf(cgNode, ssa, -1, relatedConf, null, "system call");
				}
				System.out.println("xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");
			}
			if(((SSAInvokeInstruction)ssa).getDeclaredTarget().getDeclaringClass().getName().toString().equals("Lorg/apache/hadoop/fs/FsShell")){
				retVal = true;
				System.out.println("xxxxxxxxxxxxxxxxxxcallxxxxxxxxxxxxxxxxxxxxxxx");
				System.out.println("class name" + ":" + cgNode.getMethod().getDeclaringClass().getName().toString());
				System.out.println("method name" + ":" + cgNode.getMethod().getName().toString());
				System.out.println("SSAInstruction" + ":" + ssa.toString());
				//System.out.println("line number" + ":" + IRUtil.getSourceLineNumberFromSSA(this.cgNode,ssa));
				int shellNum = 0;
				SymbolTable sT = cgNode.getIR().getSymbolTable();
				for(int i = 0; i < ssa.getNumberOfUses();i++){
					if(sT.isStringConstant(ssa.getUse(i))){
						System.out.println("string constant for shell" + ":" + sT.getValueString(ssa.getUse(i)));
						SSAUtil.markConf(cgNode, ssa, i, relatedConf, sT, "shell");
						shellNum++;
					}
				}
				if(shellNum == 0){
					SSAUtil.markConf(cgNode, ssa, -1, relatedConf, null, "shell");
				}
				System.out.println("xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");
			}
			if(((SSAInvokeInstruction)ssa).getDeclaredTarget().getDeclaringClass().getName().toString().equals("Lorg/apache/hadoop/util/GenericOptionsParser")){
				retVal = true;
				System.out.println("xxxxxxxxxxxxxxxxxxcallxxxxxxxxxxxxxxxxxxxxxxx");
				System.out.println("class name" + ":" + cgNode.getMethod().getDeclaringClass().getName().toString());
				System.out.println("method name" + ":" + cgNode.getMethod().getName().toString());
				System.out.println("SSAInstruction" + ":" + ssa.toString());
				//System.out.println("line number" + ":" + IRUtil.getSourceLineNumberFromSSA(this.cgNode,ssa));
				int parseNum = 0;
				SymbolTable sT = cgNode.getIR().getSymbolTable();
				for(int i = 0; i < ssa.getNumberOfUses();i++){
					if(sT.isStringConstant(ssa.getUse(i))){
						System.out.println("string constant for command parser" + ":" + sT.getValueString(ssa.getUse(i)));
						SSAUtil.markConf(cgNode, ssa, i, relatedConf, sT, "command parser");
						parseNum++;
					}
				}
				if(parseNum == 0){
					SSAUtil.markConf(cgNode, ssa, -1, relatedConf, null, "command parser");
				}
				System.out.println("xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");
			}
			
			/*if(((SSAInvokeInstruction)ssa).getDeclaredTarget().getDeclaringClass().getName().toString().equals("Lorg/apache/hadoop/mapred/JobConf")){
				retVal = true;
				System.out.println("xxxxxxxxxxxxxxxxxxcallxxxxxxxxxxxxxxxxxxxxxxx");
				System.out.println("class name" + ":" + cgNode.getMethod().getDeclaringClass().getName().toString());
				System.out.println("method name" + ":" + cgNode.getMethod().getName().toString());
				System.out.println("SSAInstruction" + ":" + ssa.toString());
				//System.out.println("line number" + ":" + IRUtil.getSourceLineNumberFromSSA(this.cgNode,ssa));
				int jobConf = 0;
				SymbolTable sT = cgNode.getIR().getSymbolTable();
				for(int i = 0; i < ssa.getNumberOfUses();i++){
					if(sT.isStringConstant(ssa.getUse(i))){
						System.out.println("string constant for job configuration" + ":" + sT.getValueString(ssa.getUse(i)));
						SSAUtil.markConf(cgNode, ssa, i, relatedConf, sT, "job configuration");
						jobConf++;
					}
				}
				if(jobConf == 0){
					SSAUtil.markConf(cgNode, ssa, -1, relatedConf, null, "job configuration");
				}					
				System.out.println("xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");
			}*/
			if(((SSAInvokeInstruction)ssa).getDeclaredTarget().getDeclaringClass().getName().toString().equals("Lorg/apache/hadoop/mapred/JobClient")){
				retVal = true;
				System.out.println("xxxxxxxxxxxxxxxxxxcallxxxxxxxxxxxxxxxxxxxxxxx");
				System.out.println("class name" + ":" + cgNode.getMethod().getDeclaringClass().getName().toString());
				System.out.println("method name" + ":" + cgNode.getMethod().getName().toString());
				System.out.println("SSAInstruction" + ":" + ssa.toString());
				//System.out.println("line number" + ":" + IRUtil.getSourceLineNumberFromSSA(this.cgNode,ssa));
				int jobConf = 0;
				SymbolTable sT = cgNode.getIR().getSymbolTable();
				for(int i = 0; i < ssa.getNumberOfUses();i++){
					if(sT.isStringConstant(ssa.getUse(i))){
						System.out.println("string constant for job configuration" + ":" + sT.getValueString(ssa.getUse(i)));
						SSAUtil.markConf(cgNode, ssa, i, relatedConf, sT, "job client");
						jobConf++;
					}
				}
				if(jobConf == 0){
					SSAUtil.markConf(cgNode, ssa, -1, relatedConf, null, "job client");
				}					
				System.out.println("xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");
			}
			
			if(((SSAInvokeInstruction)ssa).getDeclaredTarget().getDeclaringClass().getName().toString().equals("Lorg/apache/hadoop/fs/shell/Command")){
				retVal = true;
				System.out.println("xxxxxxxxxxxxxxxxxxcallxxxxxxxxxxxxxxxxxxxxxxx");
				System.out.println("class name" + ":" + cgNode.getMethod().getDeclaringClass().getName().toString());
				System.out.println("method name" + ":" + cgNode.getMethod().getName().toString());
				System.out.println("SSAInstruction" + ":" + ssa.toString());
				//System.out.println("line number" + ":" + IRUtil.getSourceLineNumberFromSSA(this.cgNode,ssa));
				int jobConf = 0;
				SymbolTable sT = cgNode.getIR().getSymbolTable();
				for(int i = 0; i < ssa.getNumberOfUses();i++){
					if(sT.isStringConstant(ssa.getUse(i))){
						System.out.println("string constant for shell command" + ":" + sT.getValueString(ssa.getUse(i)));
						SSAUtil.markConf(cgNode, ssa, i, relatedConf, sT, "shell command");
						jobConf++;
					}
				}
				if(jobConf == 0){
					SSAUtil.markConf(cgNode, ssa, -1, relatedConf, null, "shell command");
				}					
				System.out.println("xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");
			}
			
			
			
			if(((SSAInvokeInstruction)ssa).getDeclaredTarget().getDeclaringClass().getName().toString().equals("Lorg/apache/hadoop/hdfs/DFSClient")){
				retVal = true;
				System.out.println("xxxxxxxxxxxxxxxxxxcallxxxxxxxxxxxxxxxxxxxxxxx");
				System.out.println("class name" + ":" + cgNode.getMethod().getDeclaringClass().getName().toString());
				System.out.println("method name" + ":" + cgNode.getMethod().getName().toString());
				System.out.println("SSAInstruction" + ":" + ssa.toString());
				//System.out.println("line number" + ":" + IRUtil.getSourceLineNumberFromSSA(this.cgNode,ssa));
				int jobConf = 0;
				SymbolTable sT = cgNode.getIR().getSymbolTable();
				for(int i = 0; i < ssa.getNumberOfUses();i++){
					if(sT.isStringConstant(ssa.getUse(i))){
						System.out.println("string constant for dfs client" + ":" + sT.getValueString(ssa.getUse(i)));
						SSAUtil.markConf(cgNode, ssa, i, relatedConf, sT, "dfs client");
						jobConf++;
					}
				}
				if(jobConf == 0){
					SSAUtil.markConf(cgNode, ssa, -1, relatedConf, null, "dfs client");
				}					
				System.out.println("xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");
			}
		}
		return retVal;
	}

	public static String getParaTypeList(SSAInvokeInstruction ssa){
		String paraIntotal = new String();
		//System.out.println("getParaTypeListxxxxx" + ssa.toString());
		//System.out.println(ssa.getNumberOfParameters());
		if(ssa.isStatic() && ssa.getNumberOfParameters() == 0)
			return "cyxUchi";
		if(!ssa.isStatic() && ssa.getNumberOfParameters() == 1)
			return "cyxUchi";
		if(ssa.isStatic()){
			for(int i = 0; i < ssa.getNumberOfParameters();i++){
				//System.out.println(ssa.getDeclaredTarget().getParameterType(i).getName().toString());
				paraIntotal += ssa.getDeclaredTarget().getParameterType(i).getName().toString();
			}
			return paraIntotal;
		}else{
			for(int i = 0; i < ssa.getNumberOfParameters()-1;i++){
				//System.out.println(ssa.getDeclaredTarget().getParameterType(i).getName().toString());
				paraIntotal += ssa.getDeclaredTarget().getParameterType(i).getName().toString();
			}
			return paraIntotal;
		}
	}
	
	public static String getParaTypeList(CGNode cgn){
		String paraIntotal = new String();
		//System.out.println("getParaTypeListxxxxx" + ssa.toString());
		//System.out.println(ssa.getNumberOfParameters());
		IMethod im = cgn.getMethod();
		if(im.isStatic() && im.getNumberOfParameters() == 0)
			return "cyxUchi";
		if(!im.isStatic() && im.getNumberOfParameters() == 1)
			return "cyxUchi";
		if(im.isStatic()){
			for(int i = 0; i < im.getNumberOfParameters();i++){
				//System.out.println(im.getParameterType(i).getName().toString());
				paraIntotal += im.getParameterType(i).getName().toString();
			}
			return paraIntotal;
		}else{
			for(int i = 0; i < im.getNumberOfParameters()-1;i++){
				//System.out.println(im.getParameterType(i).getName().toString());
				paraIntotal += im.getParameterType(i).getName().toString();
			}
			return paraIntotal;
		}
	}
	
	public static HashSet<SSAInstruction> getInstructionsInSameLine(ISSABasicBlock bb, CGNode cgNode){
		HashSet<SSAInstruction> retSSAs = new HashSet<SSAInstruction>();
		int x = IRUtil.getSourceLineNumberFromBB(cgNode, bb);
		if(x != -1){
			 SSACFG cfg = cgNode.getIR().getControlFlowGraph();
			 for(Iterator<ISSABasicBlock> cfg_it = cfg.iterator(); cfg_it.hasNext(); ) {
			    ISSABasicBlock mybb = cfg_it.next();
			    for(Iterator<SSAInstruction> bb_it = mybb.iterator(); bb_it.hasNext();){
			    	SSAInstruction ssaInst = bb_it.next();
			    	if(IRUtil.getSourceLineNumberFromSSA(cgNode, ssaInst) == x)
			    		retSSAs.add(ssaInst);
			    }
			}
		}
		return retSSAs;
	}
	
	//only focus on org.apache.hadoop.hdfs.DistributedFileSystem
	public static boolean otherFileSystem(CGNode cgN){
		if(cgN.getMethod().getDeclaringClass().getName().toString().contains("Lorg/apache/hadoop/fs/viewfs/ViewFileSystem/InternalDirOfViewFs") ||
		   cgN.getMethod().getDeclaringClass().getName().toString().contains("Lorg/apache/hadoop/fs/ceph/CephFileSystem")	 ||
		   cgN.getMethod().getDeclaringClass().getName().toString().contains("Lorg/apache/hadoop/hdfs/web/WebHdfsFileSystem")||
		   cgN.getMethod().getDeclaringClass().getName().toString().contains("Lorg/apache/hadoop/fs/TestDelegationTokenRenewer/RenewableFileSystem") ||
		   cgN.getMethod().getDeclaringClass().getName().toString().contains("Lorg/apache/hadoop/fs/swift/snative/SwiftNativeFileSystem") ||
		   cgN.getMethod().getDeclaringClass().getName().toString().contains("Lorg/apache/hadoop/fs/HarFileSystem") ||
		   cgN.getMethod().getDeclaringClass().getName().toString().contains("Lorg/apache/hadoop/mapred/gridmix/PseudoLocalFs") ||
		   cgN.getMethod().getDeclaringClass().getName().toString().contains("Lorg/apache/hadoop/fs/TestFileSystemCanonicalization/DummyFileSystem")||
		   cgN.getMethod().getDeclaringClass().getName().toString().contains("Lorg/apache/hadoop/fs/FilterFileSystem") ||
		   cgN.getMethod().getDeclaringClass().getName().toString().contains("Lorg/apache/hadoop/hdfs/web/HftpFileSystem") ||
		   cgN.getMethod().getDeclaringClass().getName().toString().contains("Lorg/apache/hadoop/fs/RawLocalFileSystem") ||
		   cgN.getMethod().getDeclaringClass().getName().toString().contains("Lorg/apache/hadoop/fs/viewfs/ViewFileSystem") ||
		   cgN.getMethod().getDeclaringClass().getName().toString().contains("Lorg/apache/hadoop/fs/s3native/NativeS3FileSystem")||
		   cgN.getMethod().getDeclaringClass().getName().toString().contains("Lorg/apache/hadoop/fs/ftp/FTPFileSystem") ||
		   cgN.getMethod().getDeclaringClass().getName().toString().contains("Lorg/apache/hadoop/hdfs/web/TestTokenAspect/DummyFs") ||
		   cgN.getMethod().getDeclaringClass().getName().toString().contains("Lorg/apache/hadoop/fs/s3/S3FileSystem") ||
		   cgN.getMethod().getDeclaringClass().getName().toString().contains("Lorg/kitesdk/morphline/hadoop/rcfile/SingleStreamFileSystem"))
			return true;
		return false;
		  
	}
	
	//only focus on org.apache.hadoop.hdfs.DistributedFileSystem
	public static boolean otherFileSystem(String libName){
		if(libName.contains("Lorg/apache/hadoop/fs/viewfs/ViewFileSystem/InternalDirOfViewFs") ||
				libName.contains("Lorg/apache/hadoop/fs/ceph/CephFileSystem")	 ||
				libName.contains("Lorg/apache/hadoop/hdfs/web/WebHdfsFileSystem")||
				libName.contains("Lorg/apache/hadoop/fs/TestDelegationTokenRenewer/RenewableFileSystem") ||
				libName.contains("Lorg/apache/hadoop/fs/swift/snative/SwiftNativeFileSystem") ||
				libName.contains("Lorg/apache/hadoop/fs/HarFileSystem") ||
				libName.contains("Lorg/apache/hadoop/mapred/gridmix/PseudoLocalFs") ||
				libName.contains("Lorg/apache/hadoop/fs/TestFileSystemCanonicalization/DummyFileSystem")||
				libName.contains("Lorg/apache/hadoop/fs/FilterFileSystem") ||
				libName.contains("Lorg/apache/hadoop/hdfs/web/HftpFileSystem") ||
				libName.contains("Lorg/apache/hadoop/fs/RawLocalFileSystem") ||
				libName.contains("Lorg/apache/hadoop/fs/viewfs/ViewFileSystem") ||
				libName.contains("Lorg/apache/hadoop/fs/s3native/NativeS3FileSystem")||
				libName.contains("Lorg/apache/hadoop/fs/ftp/FTPFileSystem") ||
				libName.contains("Lorg/apache/hadoop/hdfs/web/TestTokenAspect/DummyFs") ||
				libName.contains("Lorg/apache/hadoop/fs/s3/S3FileSystem") ||
				libName.contains("Lorg/kitesdk/morphline/hadoop/rcfile/SingleStreamFileSystem"))
			return true;
		return false;
		  
	}
	
	public static boolean twoClassesDerived(CGNode cgn1, CGNode cgn2){
		if(cgn1.equals(cgn2))
			return true;
		//System.out.println("find all super classes for " + cgn1.getMethod().getDeclaringClass().getName().toString());
		HashSet<IClass> allSuperClass = new HashSet<IClass>();
		IClass tmp = cgn1.getMethod().getDeclaringClass();
		LinkedList<IClass> work_list = new LinkedList<IClass>();
		if(tmp.getSuperclass() == null)
			return false;
		work_list.add(tmp);
		allSuperClass.add(tmp);
		while(!work_list.isEmpty()){
			IClass new_class = work_list.poll().getSuperclass();
			if(new_class != null && !allSuperClass.contains(new_class)){
				allSuperClass.add(new_class);
				//System.out.println("find one super class" + new_class.getName().toString());
			}
		}
		IClass cgn2Class = cgn2.getMethod().getDeclaringClass();
		if(allSuperClass.contains(cgn2Class))
			return true;
		else
			return false;
	}
	
	public static boolean findAddOrMinus(boolean needAdd, CGNode cgn, int var){
		boolean retVal = false;
		System.out.println("xxxxxxx" + var);
		 SSACFG cfg = cgn.getIR().getControlFlowGraph();
		    for (Iterator<ISSABasicBlock> cfg_it = cfg.iterator(); cfg_it.hasNext(); ) {
		    	ISSABasicBlock bb = cfg_it.next();
		    	for(Iterator<SSAInstruction> bb_it = bb.iterator(); bb_it.hasNext();){
		    		SSAInstruction ssaInst = bb_it.next();
		    		if(ssaInst instanceof SSABinaryOpInstruction){
		    			if(ssaInst.getUse(0) == var){
		    				System.out.println("xxxxxx" + ssaInst.toString());
		    				if(((SSABinaryOpInstruction) ssaInst).getOperator().equals(IBinaryOpInstruction.Operator.ADD)){
	    						SSAInstruction myTmp = null;
		    					if(SSAUtil.isConstant(cgn,ssaInst.getUse(1))){
		    						String op2 = SSAUtil.getConstant(cgn, ssaInst.getUse(1));
		    		    			if(!op2.equals("null") && Float.valueOf(op2) >= 0 ){
		    		    				if(needAdd){
		    		    					return true;
		    		    				}
		    		    			}else if(!op2.equals("null") && Float.valueOf(op2) <= 0){
		    		    				if(!needAdd){
		    		    					return true;
		    		    				}
		    		    			}
		    		    		}else if((myTmp = SSAUtil.getSSAByDU(cgn,ssaInst.getUse(1)))!=null){
	    		    				return true;
	    		    			}
		    				}
		    				if(((SSABinaryOpInstruction) ssaInst).getOperator().equals(IBinaryOpInstruction.Operator.SUB)){
	    						SSAInstruction myTmp = null;
		    					if(SSAUtil.isConstant(cgn,ssaInst.getUse(1))){
		    						String op2 = SSAUtil.getConstant(cgn, ssaInst.getUse(1));
		    		    			if(!op2.equals("null") && Float.valueOf(op2) >= 0 ){
		    		    				if(!needAdd){
		    		    					return true;
		    		    				}
		    		    			}else if(!op2.equals("null") && Float.valueOf(op2)<= 0){
		    		    				if(needAdd){
		    		    					return true;
		    		    				}
		    		    			}
		    		    		}else if((myTmp = SSAUtil.getSSAByDU(cgn,ssaInst.getUse(1)))!=null){
	    		    				return true;
	    		    			}
		    				}
		    			}
		    		}
		    	}
		    }
		return retVal;
	}
	//determine a loop is a case like 
	// 1. while(a >=0 ){a--}
	// 2. while(a <=0 ){a++}
	public static boolean isSpecialLoop(SSAInstruction ssa, CGNode cgn){
		boolean retVal = false;
		if(ssa instanceof SSAConditionalBranchInstruction){
			// a <= 0 or a < 0
			if(((SSAConditionalBranchInstruction) ssa).getOperator().equals(IConditionalBranchInstruction.Operator.GT) ||
					((SSAConditionalBranchInstruction) ssa).getOperator().equals(IConditionalBranchInstruction.Operator.GE)	){
				if(findAddOrMinus(true,cgn,ssa.getUse(0)))
					return true;
			}
			// a >=0 or a > 0
			if(((SSAConditionalBranchInstruction) ssa).getOperator().equals(IConditionalBranchInstruction.Operator.LT) ||
					((SSAConditionalBranchInstruction) ssa).getOperator().equals(IConditionalBranchInstruction.Operator.LE)	){
				if(findAddOrMinus(false,cgn,ssa.getUse(0)))
					return true;
			}
		}
		return retVal;
	}
	
	/*public static boolean isSpecialCollection4CA(String varName, String className){
		boolean retVal = false;
		if(className.equals("Lorg/apache/cassandra/db/index/SecondaryIndexManager") && varName.equals("indexesByColumn"))
			return true;
		if(varName.equals("null") && (className.equals("Lorg/apache/cassandra/io/sstable/SSTableScanner")) ||
				className.equals("Lorg/apache/cassandra/locator/AbstractReplicationStrategy") ||
				className.equals("Lorg/apache/cassandra/db/compaction/CompactionManager")
				)
			return true;

		return retVal;
	}*/
	
	public static CGNode findTrueCaller4Run(CallGraph cg, String className, String target){
		CGNode ret = null;
		for (Iterator<? extends CGNode> it = cg.iterator(); it.hasNext(); ) {
    		CGNode f = it.next();
	      	if ( LoopVarUtil.isApplicationMethod(f) ) {
	      		if ( !LoopVarUtil.isNativeMethod(f)){
	      			if(!f.getMethod().getDeclaringClass().getName().toString().equals(className))
	      				continue;
	      			SSACFG cfg = f.getIR().getControlFlowGraph();
	      			for (Iterator<ISSABasicBlock> cfg_it = cfg.iterator(); cfg_it.hasNext(); ) {
	      			    ISSABasicBlock bb = cfg_it.next();
	      			    for(Iterator<SSAInstruction> bb_it = bb.iterator(); bb_it.hasNext();){
	      			    	SSAInstruction ssaInst = bb_it.next();
	      			    	if(ssaInst instanceof SSANewInstruction){
	      			    		if(((SSANewInstruction) ssaInst).getNewSite().getDeclaredType().getName().toString().equals(target))
	      			    			return f;
	      			    	}
	      			    }
	      			 }
	      		}
	      	}
	    }
		return ret;
	}
	
	public static boolean isCollectionEmpty(SSAInvokeInstruction ssa){
		boolean ret = false;
		if(ssa.getDeclaredTarget().getDeclaringClass().getName().toString().equals("Ljava/util/Collections") &&(
				ssa.getDeclaredTarget().getName().toString().equals("emptyList")) ||
				ssa.getDeclaredTarget().getName().toString().equals("emptyMap") ||
				ssa.getDeclaredTarget().getName().toString().equals("emptySet")){
			ret = true;
		}
		return ret;
	}
	
	public static boolean findInitializationInBetween(CGNode cgn, CGNode cgn2,SSAInstruction callSite){
		IR ir = cgn.getIR();
		SSACFG cfg = ir.getControlFlowGraph();
		boolean encounter_it = false;
		for(Iterator<ISSABasicBlock> cfg_it = cfg.iterator(); cfg_it.hasNext() && !encounter_it;){
			ISSABasicBlock bb = cfg_it.next();
			for(Iterator<SSAInstruction> bb_it = bb.iterator(); bb_it.hasNext();){
				SSAInstruction ssaInst = bb_it.next();
				if(ssaInst.equals(callSite)){
					encounter_it = true;
				}
				if(ssaInst instanceof SSANewInstruction){
					if(((SSANewInstruction)ssaInst).getNewSite().getDeclaredType().getName().toString().equals(cgn2.getMethod().getDeclaringClass().getName().toString())){
						//System.out.println("xxxxx" + ((SSANewInstruction)ssaInst).getNewSite().getDeclaredType().getName().toString());
						return true;
					}
				}
			}
		}
		return false;
	}
	
	public static boolean hasFieldVar(CGNode cgn, int paraLoc){

		IClass tmp = cgn.getMethod().getDeclaringClass();
		HashSet<String> name = new HashSet<String>();
		for(IField x :tmp.getAllFields()){
			name.add(x.getName().toString());
		}
				
		int m = 0;
		if(cgn.getMethod().isStatic())
			m = paraLoc;
		else
			m = paraLoc + 1;
				
		if(name.contains(Integer.toString(m)))
			return true;
		return false;
	}
}
