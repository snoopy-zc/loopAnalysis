package sa.loopsize;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ssa.SSAFieldAccessInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.types.ClassLoaderReference;
import com.text.Checker;

import sa.loop.LoopAnalyzer;
import sa.wala.IRUtil;

public class ModelLibrary {
	final static String commonPath = "src/sa/loopsize/res/commonLib.txt";
	//final static String txtPath = "src/sa/loopsize/res/modelLib-mr-4831.txt";
	//final static String txtPath = "src/sa/loopsize/res/modelLib-hdfs-4584.txt";
	 //final static String txtPath = "src/sa/loopsize/res/modelLib-hdfs-5153.txt";
	//final static String txtPath = "src/sa/loopsize/res/modelLib-mr-2705.txt";
	//final static String txtPath = "src/sa/loopsize/res/modelLib-mr-4576.txt";
	//final static String txtPath = "src/sa/loopsize/res/modelLib-mr-4088.txt";

	static Checker ck;
	
	public static int[] whichVneedAnalyze(SSAInvokeInstruction ssa, CGNode cgn){
		int[] ret = new int[10];
		Arrays.fill(ret, -1);
		File input = new File("/home/c-lab/eclipse-workspace/loopAnalysis/src/sa/loopsize/res/commonLib.txt");
		String funcName = ssa.getDeclaredTarget().getName().toString();
		String libName = ssa.getDeclaredTarget().getDeclaringClass().getName().toString();
		String paraTypes = SSAUtil.getParaTypeList(ssa);
		try (BufferedReader br = new BufferedReader(new FileReader(input))) {
		    String line;
		    while ((line = br.readLine()) != null) {
		    	if(line.startsWith("#"))
		    		continue;
		    	String[] info = line.trim().split("\\s+");
		    	//System.out.println(info.length);
		    	/*System.out.println(funcName);
		    	System.out.println(libName);
		    	System.out.println(paraTypes);
		    	System.out.println("??????????????");
		    	System.out.println(info[0]);
		    	System.out.println(info[1]);
		    	System.out.println(info[2]);
		    	System.out.println(info[2].equals("0"));
		    	System.out.println(paraTypes.equals("cyxUchi"));*/
		    	assert info.length >= 4;
		    	if(info[0].equals(libName) && info[1].equals(funcName) && (info[2].equals(paraTypes) ||
		    			(info[2].equals("0") && paraTypes.equals("cyxUchi")))){
		    		br.close();
		    		int j = 0;
		    		for(int i = 3; i < info.length;i++){
		    			ret[j] = Integer.parseInt(info[i]);
		    			j++;
		    			
		    		}
		    		return ret;
		    	}
		    }
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return ret;
	}
	
	public static void addFile(String txtPath){
		ck = new Checker();
		ck.addCheckFile(txtPath);
	}
	
	public static boolean funcWithParaInModelLib(SSAInvokeInstruction ssa,CGNode cgn, String paraList){
		String funcName = ssa.getDeclaredTarget().getName().toString();
		String libName = ssa.getDeclaredTarget().getDeclaringClass().getName().toString();
		String paraTypes = SSAUtil.getParaTypeList(ssa);
		//System.out.println("xxxxxxxxxxxxxxxxxxxx" + funcName);
		//System.out.println("xxxxxxxxxxxxxxxxxxxx" + libName);
		//System.out.println("xxxxxxxxxxxxxxxxxxxx" + paraList);
		//System.out.println(ck.isSplitTarget(funcName,libName,paraTypes));
		//if(paraList.equals(""))
		if( !paraList.equals("cyxUchi") && ck.isSplitTargetSpecial(libName,funcName,paraTypes)){
			return true;
		}
		return false;
	}
	
	public static boolean libInModelLib(SSAInvokeInstruction ssa, CGNode cgn){
		String libName = ssa.getDeclaredTarget().getDeclaringClass().getName().toString();
		return ck.isSplitTargetSpecial(libName,"*", "*");
	}
	
	public static boolean libInModelLib(String libName){
		return ck.isSplitTargetSpecial(libName,"*", "*");
	}
	
	public static boolean funcInModelLib(SSAInvokeInstruction ssa, CGNode cgn){
		String funcName = ssa.getDeclaredTarget().getName().toString();
		String libName = ssa.getDeclaredTarget().getDeclaringClass().getName().toString();
		return ck.isSplitTargetSpecial(libName,funcName,"*");  
	}
	
	public static boolean isJavaIOorUtil(SSAInvokeInstruction ssa){
		String libName = ssa.getDeclaredTarget().getDeclaringClass().getName().toString();
		if(libName.startsWith("Ljava/io/") || (libName.startsWith("Lorg/apache/hadoop/io") && !libName.equals("Lorg/apache/hadoop/io/IOUtils"))|| (libName.endsWith("Utils") && !libName.equals("Lorg/apache/hadoop/io/IOUtils")))
			return true;
		return false;
	}
	
	//public static boolean isFS(SSAInvokeInstruction ssa){
	//	String libName = ssa.getDeclaredTarget().getDeclaringClass().getName().toString();
	//	if(libName.contains("Lorg/apache/hadoop/fs/"))
	//		return true;
	//	return false;
	//}
	
	public static boolean isModeled(String txtPath, SSAInvokeInstruction ssa, CGNode cgn, String paraList){
		addFile(txtPath);
		return funcInModelLib(ssa,cgn) || libInModelLib(ssa,cgn) || funcWithParaInModelLib(ssa,cgn,paraList) || isJavaIOorUtil(ssa);//|| isFS(ssa);
	}
	
	public static boolean isModeled4Loop(String txtPath, CGNode cgn){
		addFile(txtPath);
		String className = cgn.getMethod().getDeclaringClass().getName().toString();
		String methodName = cgn.getMethod().getName().toString();
		String paraList = SSAUtil.getParaTypeList(cgn);
		if(SSAUtil.otherFileSystem(cgn))
			return true;
		if(paraList.equals("cyxUchi"))
			return className.contains("/util/") || className.endsWith("Util") || className.startsWith("Lorg/apache/hadoop/io") || ck.isSplitTargetSpecial(className,"*","*") || ck.isSplitTargetSpecial(className,methodName,"*") || className.startsWith("Ljava/io") || cgn.getMethod().getDeclaringClass().getClassLoader().getName().toString().equals("Primordial");
		else
			return className.contains("/util/") || className.endsWith("Util") || className.startsWith("Lorg/apache/hadoop/io") || ck.isSplitTargetSpecial(className,methodName,paraList) ||ck.isSplitTargetSpecial(className,"*","*") || ck.isSplitTargetSpecial(className,methodName,"*") || className.startsWith("Ljava/io") || cgn.getMethod().getDeclaringClass().getClassLoader().getName().toString().equals("Primordial");
	}
	
	public static boolean isPrimordial(SSAInstruction dInst,CGNode cgn){
		//System.out.println("wssssssssssss"+ ((SSAInvokeInstruction)dInst).getDeclaredTarget().getDeclaringClass().getClassLoader().getName().toString());
		if(((SSAInvokeInstruction)dInst).getDeclaredTarget().getDeclaringClass().getClassLoader().getName().toString().equals("Primordial")){
			return true;
		}else
			return false;
	}
	
	public static boolean isNormalIteratorMethod(SSAInvokeInstruction ssa){
		//System.out.println("xxxxxxxx"+ssa.getDeclaredTarget().getName().toString());
		//System.out.println("xxxxxxxxxx"+ssa.getDeclaredTarget().getDeclaringClass().getName().toString());
		//System.out.println("xxxxxx" + ssa.getDeclaredResultType().getName().toString());
		
		
		if(ssa.getDeclaredTarget().getDeclaringClass().getName().toString().equals("Ljava/util/Iterator")){
			if(ssa.getDeclaredTarget().getName().toString().equals("next") || ssa.getDeclaredTarget().getName().toString().equals("hasNext"))
				return true;
		}
		
		if(ssa.getDeclaredResultType().getName().toString().equals("Ljava/util/Iterator")){
			if(ssa.getDeclaredTarget().getName().toString().equals("iterator")){
				return true;
			}
		}
		return false;
	}
	
	// user defined iterator 
	public static HashSet<MyTriple> isUserDefinedIterator(SSAInvokeInstruction ssa,CallGraph cg,LoopAnalyzer looper,CGNode cgn, String txtPath, HashSet<MyPair> whoHasRun){
		HashSet<MyTriple> ret = new HashSet<MyTriple>();
		if(ssa == null)
			return ret;
		System.out.println(ssa.toString());
		if(ssa.getDeclaredTarget().getName().toString().equals("iterator")){
			System.out.println("normal iterator");
		}
		else if(ssa.getDeclaredTarget().getName().toString().equals("dirIterator")){
			System.out.println("dirIterator");
			ArrayList<LabelledSSA> retSSAs = (ArrayList<LabelledSSA>) SSAUtil.getSSAVariable("org.apache.hadoop.hdfs.server.common.Storage$DirIterator.remove-157",cg);
			for(LabelledSSA x : retSSAs){
				if(x.getSSA() instanceof SSAInvokeInstruction){
					SSAInstruction retSSA = SSAUtil.getSSAByDU(x.getCGNode(),x.getSSA().getUse(0));
					System.out.println(retSSA.toString());
					//System.out.println("sbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
			
					String varName = ((SSAFieldAccessInstruction)retSSA).getDeclaredField().getName().toString();
					String varType = ((SSAFieldAccessInstruction)retSSA).getDeclaredFieldType().getName().toString();
					String varInClass =((SSAFieldAccessInstruction)retSSA).getDeclaredField().getDeclaringClass().getName().toString();
					
					//System.out.println(varName);
					//System.out.println(varType);
					
					CollectionInst collObj = new CollectionInst(cg,x.getCGNode(),retSSA,varName,varType,varInClass,looper,txtPath,whoHasRun);
					collObj.doWork();
					collObj.printResult();
					if(collObj.loopCond.size() != 0)
						ret.addAll(collObj.loopCond);
					if(collObj.allFieldInsts.size() != 0)
						ret.addAll(collObj.allFieldInsts);
					if(collObj.fieldCollectionInit.size() != 0)
						ret.addAll(collObj.fieldCollectionInit);
					//assert 0 == 1;
				}
			}
		}else if(ssa.getDeclaredTarget().getName().toString().equals("nodeIterator") && ssa.getDeclaredTarget().getDeclaringClass().getName().toString().equals("Lorg/apache/hadoop/hdfs/server/blockmanagement/BlocksMap")){
			System.out.println("nodeIterator");
		}else if(ssa.getDeclaredTarget().getName().toString().equals("getBlockIterator") && ssa.getDeclaredTarget().getDeclaringClass().getName().toString().equals("Lorg/apache/hadoop/hdfs/server/blockmanagement/DatanodeDescriptor")){
			System.out.println("getBlockIterator");
		}else{
			System.out.println(ssa);
			System.out.println("other user defined iterator we need model");
			System.out.println("xxxxxxxxxxxxxxxxxnewxxxxxxxxxxxxxxxxxxxxxxxxx");
			System.out.println("class name" + ":" + cgn.getMethod().getDeclaringClass().getName().toString());
			System.out.println("method name" + ":" + cgn.getMethod().getName().toString());
			System.out.println("SSAInstruction" + ":" + ssa.toString());
			System.out.println("line number" + ":" + IRUtil.getSourceLineNumberFromSSA(cgn,ssa));
			System.out.println("xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");
			//assert 0 == 1;
		}
		return ret;
	}
}
