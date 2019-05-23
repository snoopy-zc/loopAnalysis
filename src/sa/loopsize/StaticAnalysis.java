package sa.loopsize;

import sa.loop.LoopAnalyzer;
import sa.loop.LoopInfo;
import sa.wala.IRUtil;
import sa.wala.WalaAnalyzer;
import sun.misc.Queue;

import java.beans.Statement;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
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
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.slicer.PDG;
import com.ibm.wala.ipa.slicer.SDG;
import com.ibm.wala.shrikeBT.*;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSACFG;
import com.ibm.wala.ssa.SSAConditionalBranchInstruction;
import com.ibm.wala.ssa.SSAFieldAccessInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SymbolTable;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.util.WalaException;
import com.text.TextFileWriter;

public class StaticAnalysis {
	
	WalaAnalyzer walaAnalyzer;
    ClassHierarchy cha;
    CallGraph cg;
    CGNode cNode;
    LoopAnalyzer looper;
    //PointerAnalysis pa;
    SSAInstruction fakeInst = null;
    HashSet<MyPair> whoHashRun = new HashSet<MyPair>();
    
	public StaticAnalysis(String jarLoc) {
		//this.walaAnalyzer = new WalaAnalyzer("src/sa/loopsize");
		//this.walaAnalyzer = new WalaAnalyzer("src/sa/res/ha-4584");
		//this.walaAnalyzer = new WalaAnalyzer("src/sa/res/mr-4813");
		//this.walaAnalyzer = new WalaAnalyzer(jarLoc);
		//this.walaAnalyzer = new WalaAnalyzer("src/sa/res/mr-2705");
		//this.walaAnalyzer = new WalaAnalyzer("src/sa/res/mr-4576");
		//this.walaAnalyzer = new WalaAnalyzer("src/sa/res/mr-4088");

		this.walaAnalyzer = new WalaAnalyzer(jarLoc);		
		
		this.cha = this.walaAnalyzer.getClassHierarchy();
		this.cg = this.walaAnalyzer.getCallGraph();
		this.looper = new LoopAnalyzer(this.walaAnalyzer);
		//this.pa = this.walaAnalyzer.getAliasModel();
	}
	
	// input: loop path 
	// path info should be like org.apache.hadoop.hdfs.server.datanode.FSDataset$FSVolumeSet.getBlockInfo-679
	// return List<SSAInstruction>
	public List<SSAInstruction> getLoopVariable(String loopPath){
		String[] splitPath = loopPath.split("-");
		assert splitPath.length == 2;
		/*for(String partStr: splitPath)
			System.out.println(partStr);*/
		String methPath = splitPath[0];
		int loopLine = Integer.parseInt(splitPath[1]);
		CallGraph cg = this.walaAnalyzer.getCallGraph();
		this.whoHashRun.addAll(ParameterInst.getCaller4ThreadRun(cg));
		IR ir = null;
		IMethod im = null;
		List<SSAInstruction> loopSSA = new ArrayList<SSAInstruction>();
    	for (Iterator<? extends CGNode> it = cg.iterator(); it.hasNext(); ) {
    		CGNode f = it.next();
	      	if ( LoopVarUtil.isApplicationMethod(f) ) {
	      		if ( !LoopVarUtil.isNativeMethod(f)){
	      			im = f.getMethod();
	      			//System.out.println(im.getSignature().toString());
	      			//System.out.println("xxxxxxxxxxxxxxx");
	      			//System.out.println(methPath.toString());
	      			if(im.getSignature().indexOf(methPath)>=0){
	      				ir = f.getIR();
	      				//For test, generate CFG 
	      				//System.out.println(f.getIR().toString());			 				
	      				try{
	      					LoopVarUtil.generateViewIR(f.getIR(), cha);
	      				}catch(WalaException e){
	      					e.printStackTrace();
	      				}
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
	      			    			loopSSA.add(ssaInst);
	      			    			if(this.cNode == null)
	      			    				this.cNode = f;
	      			    		}
	      			    	}
	      			    }
	      			}
	      		}
	      	}
    	}
    	if(loopSSA.size() == 0)
    		throw new RuntimeException("please check input for locating loop");
		return loopSSA;
	}
	
	public void printAllIR(){
		CallGraph cg = this.walaAnalyzer.getCallGraph();
		TextFileWriter x = new TextFileWriter("/home/c-lab/outFig/ir.txt");
		IR ir = null;
		IMethod im = null;
		List<SSAInstruction> loopSSA = new ArrayList<SSAInstruction>();
    	for (Iterator<? extends CGNode> it = cg.iterator(); it.hasNext(); ) {
    		CGNode f = it.next();
	      	if ( LoopVarUtil.isApplicationMethod(f) ) {
	      		if ( !LoopVarUtil.isNativeMethod(f)){
	      			im = f.getMethod();
	      			ir = f.getIR();
	      			x.writeLine(ir.toString());
	      		}
	      	}
    	}
    	x.close();
	}
	
	public SSAInstruction getCondVar(List<SSAInstruction> loopSSA){
		int condNum = 0;
		SSAInstruction myInst = null;
		for(SSAInstruction ssaInst:loopSSA){
			this.fakeInst = ssaInst;
			if (ssaInst instanceof  SSAConditionalBranchInstruction ){
				//if(((SSAConditionalBranchInstruction) ssaInst).getOperator().toString().equals("ge"))
				//	System.out.println("the operator is: !");
				//System.out.println(((SSAConditionalBranchInstruction) ssaInst).getType().toString());
				//System.out.println(ssaInst.toString());logEdit
				//System.out.println(((SSAConditionalBranchInstruction) ssaInst).getOperator());
				//SSAUtil.testDef(ssaInst);
				//SSAUtil.testUse(ssaInst);
				condNum++;
				if(myInst == null)
					myInst = ssaInst;
			}
		}
		if(condNum == 1){ // we can extend it for more than one branch instructions
			System.out.println("potential cond var:" + myInst.toString());
			return myInst;
		}
		return myInst;
	}
		
	public static void main(String[] args) throws IOException {
	
		PrintStream ps_console = System.out;

		//File input = new File("/home/nemo/workspace/JXCascading-detector/src/sa/loopsize/res/input-hdfs-4584.txt");
		//File input = new File("/home/nemo/workspace/JXCascading-detector/src/sa/loopsize/res/input-mr-4813.txt");
		//File input = new File("/home/nemo/workspace/JXCascading-detector/src/sa/loopsize/res/input-hdfs-5153.txt");
		//File input = new File("/home/nemo/workspace/JXCascading-detector/src/sa/loopsize/res/input-mr-2705.txt");
		//File input = new File("/home/nemo/workspace/JXCascading-detector/src/sa/loopsize/res/input-mr-4088.txt");
		//File input = new File("/home/nemo/workspace/JXCascading-detector/src/sa/loopsize/res/input-mr-4576.txt");

		File generalInput = new File("/home/c-lab/eclipse-workspace/loopAnalysis/src/sa/loopsize/res/input.txt");
		long total_time = 0;
		try(BufferedReader perBug = new BufferedReader(new FileReader(generalInput))){
			String perLine;
			while((perLine = perBug.readLine()) != null){
				if(perLine.startsWith("#"))
					continue;
				String[] perBugInput = perLine.trim().split("\\s+");
				assert perBugInput.length == 4;
				String inputLoc = perBugInput[0];
				String logLoc = perBugInput[1];
				String jarLoc = perBugInput[2];
				String modelLibLoc = perBugInput[3];
			
				System.out.println("input location " + inputLoc);
				System.out.println("log location " + logLoc);
				System.out.println("jar files location " + jarLoc);
				System.out.println("modelLib location " + modelLibLoc);
				try (BufferedReader br = new BufferedReader(new FileReader(inputLoc))) {
				    String line;
				    while ((line = br.readLine()) != null) {
				    	if(line.startsWith("#"))
				    		continue;
				    	line = line.trim().split("\\s+")[0];
				    	String nameTag = line.split("-")[1];
				    	/*Path logPath = Statistic.createFile(logLoc,nameTag);
				    	System.err.println("create file" + logPath.toString());
				    	PrintStream recorder = Statistic.writeToFileRawBegin(logPath);*/
						List<SSAInstruction> loopSSAInst = new ArrayList<SSAInstruction>();
						HashSet<MyTuple> resultBuff = new HashSet<MyTuple>();
						HashSet<MyTriple> bList = new HashSet<MyTriple>();
						StaticAnalysis sHelper = new StaticAnalysis(jarLoc);
						sHelper.printAllIR();
					    loopSSAInst = sHelper.getLoopVariable(line);
						SSAInstruction ssa_test = sHelper.getCondVar(loopSSAInst);
						/*MultiConds x = new MultiConds(sHelper.cg,sHelper.cNode,ssa_test,sHelper.looper);
						x.doWorks();*/
						assert 0 == 1;
						/*ParameterInst.isDerivedThreadRun(sHelper.cNode);
						System.out.println(sHelper.whoHashRun.size());
						for(MyPair m : sHelper.whoHashRun){
							System.out.println(m.getClassName().toString());
							System.out.println(m.getSSA().toString());
							System.out.println(m.getCGN().getMethod().getName().toString());
						}*/
						//ParameterInst.getCaller4ThreadRun(sHelper.cg);
						//assert 0 == 1;
						long startTime = System.currentTimeMillis();

						//loopSSAInst = sHelper.getLoopVariable(line);
						//loopSSAInst = sHelper.getLoopVariable("testFor1-19");
						SSAInstruction ssa = sHelper.getCondVar(loopSSAInst);
						
						//AliasAnalysis.getAA(sHelper.pa,sHelper.cNode, 27);
						//assert 0 == 1;
							
						if(ssa == null){
							int instIndex = IRUtil.getSSAIndex(sHelper.cNode,sHelper.fakeInst);
							System.out.println(sHelper.fakeInst.toString());
							System.out.println(instIndex);
							if(instIndex != -1 && LoopCond.isWhileTrueCase(sHelper.cNode.getIR().getControlFlowGraph().getBlockForInstruction(instIndex),sHelper.cNode)){
								System.out.println("it's while true case, please manually check it");
								Statistic.writeToFileRawEnd(ps_console);
								System.out.println("finish processing");
								continue;
							}
						}
						assert ssa != null;
						int level = 0;
						LinkedList<ProcessUnit> processList = new LinkedList<ProcessUnit>();
						ProcessUnit unit = new ProcessUnit(sHelper.cg,sHelper.cNode,ssa,true,0,-1,sHelper.looper,modelLibLoc,null,sHelper.whoHashRun);
						//System.out.println("this loop is bounded or not" + ":" + SSAUtil.isBounded(unit,sHelper.looper,resultBuff,bList));
						//System.out.println("size of modification location" + ":" + bList.size());
						
						
						SSAUtil.isBoundedSearch(unit,sHelper.looper, bList,modelLibLoc,sHelper.whoHashRun);
						
	
						
				    	Path resultPath = Statistic.createFile(logLoc, nameTag + ".result");
				    	PrintStream recorder2 = Statistic.writeToFileRawBegin(resultPath);
						System.out.println("size of result buff" + bList.size());
						/*for(MyTriple ret : bList){
							System.out.println("^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^");
							System.out.println("class name" + ":" + ret.getCGNode().getMethod().getDeclaringClass().getName().toString());
							System.out.println("method name" + ":" + ret.getCGNode().getMethod().getName().toString());
							System.out.println("SSAInstruction" + ":" + ret.getSSA().toString());
							System.out.println("paraLoc" + ":" + SSAUtil.paraLocTrans(ret.getSSA(),ret.getCGNode(), ret.getParaLoc()));
							System.out.println("line number" + ":" + IRUtil.getSourceLineNumberFromSSA(ret.getCGNode(),ret.getSSA()));
							if(ret.hasInfo()){
								System.out.println("need manual checking");
								for(String ccc : ret.info)
									System.out.println(ccc);
							}
							System.out.println("^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^");
						}*/
		
						int outPoint = 0;
						int notNeedCheck = 0;
						int confRelated = 0;
						int constantRelated = 0;
						int maybeRPC = 0;
						int isCABounded = 0;
						HashSet<MyTriple> needCkPoint = new HashSet<MyTriple>();
						for(MyTriple ret : bList){
							if(ret.getHasChild() == false){
								outPoint++;
								boolean realBounded = false;
								boolean confBounded = false;
								System.out.println("%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%");
								System.out.println("class name" + ":" + ret.getCGNode().getMethod().getDeclaringClass().getName().toString());
								System.out.println("method name" + ":" + ret.getCGNode().getMethod().getName().toString());
								System.out.println("SSAInstruction" + ":" + ret.getSSA().toString());
								System.out.println("paraLoc" + ":" + SSAUtil.paraLocTrans(ret.getSSA(),ret.getCGNode(), ret.getParaLoc()));
								System.out.println("line number" + ":" + IRUtil.getSourceLineNumberFromSSA(ret.getCGNode(),ret.getSSA()));
								System.out.println("bounded or not" + ":"+ ret.getBounded());
								if(ret.getBounded() == true)
									System.out.println("it's strongly bounded, no need check");
								else{
									if(ret.getParaLoc() == -2)
										System.out.println("it's while true case, need check");
									if(ret.getParaLoc() == -3)
										System.out.println("it's special iterator, need check");
									if(ret.getParaLoc() == -4)
										System.out.println("can't determine the branch condition, need check");
									if(ret.getParaLoc() == -5)
										System.out.println("compare condtion, need check");
									if(ret.getParaLoc() == -6)
										System.out.println("not the special loop(a++,a--),need check");
									if(ret.getParaLoc() == -7)
										System.out.println("it's while(boolean), need check");
									if(ret.getParaLoc() == -8)
										System.out.println("user command, need check");
									if(ret.hasInfo()){
										System.out.println("need manual checking");
										for(String ccc : ret.info)
											System.out.println(ccc);
									}
								}
								
								if(ret.getBounded() == true){
									System.out.println("no need to check");
									notNeedCheck++;	
									realBounded = true;
								}
								
								if(ret.getNotFoundCaller() == true && ret.getBounded() == false){
									System.out.println("not find caller, maybe RPC, need check");
									maybeRPC++;
								}
								
								if(ret.hasconfRelated()){
									if(realBounded){
										confBounded = true;
										confRelated++;
									}
									System.out.println("print related conf");
									System.out.println("xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");
									for(String tmp : ret.confVar)
										System.out.println(tmp);
									System.out.println("xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");
								}
								if(ret.hasConstantVar()){
									if(realBounded && (confBounded == false)){
										constantRelated++;
									}
									System.out.println("print related constant");
									System.out.println("********************************");
									for(String tmp : ret.constantVar)
										System.out.println(tmp);
									System.out.println("********************************");
								}
								
								if(!ret.hasconfRelated() && !ret.hasConstantVar() && ret.getCABounded()){
									isCABounded++;
									System.out.println("array/collection size bounded");
								}
								if(!realBounded)
									needCkPoint.add(ret);
								System.out.println("%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%");
							}
						}
						System.out.println("size of conf bounded " + confRelated);
						System.out.println("size of constant bounded " + constantRelated);
						System.out.println("size of manually checking " + (outPoint - notNeedCheck));
						System.out.println("size of maybe RPC " + maybeRPC);
						System.out.println("size of bounded array/collection" + isCABounded);
						
				    	Path instrPath = Statistic.createFile(logLoc, nameTag + ".instr");
				    	PrintStream recorder3 = Statistic.writeToFileRawBegin(instrPath);
						System.out.println("print the point we need check");
						
						for(MyTriple ret : needCkPoint){
							System.out.println(ret.getCGNode().getMethod().getDeclaringClass().getName().toString() + " " + 
									ret.getCGNode().getMethod().getName().toString() + " " +
									IRUtil.getSourceLineNumberFromSSA(ret.getCGNode(),ret.getSSA()) + " " + nameTag + " " + ret.getParaLoc());
							/*System.out.println("++++++++++++++++++++++++++++++++++++++++++++++++");
							System.out.println("class name" + ":" + ret.getCGNode().getMethod().getDeclaringClass().getName().toString());
							System.out.println("method name" + ":" + ret.getCGNode().getMethod().getName().toString());
							System.out.println("SSAInstruction" + ":" + ret.getSSA().toString());
							System.out.println("paraLoc" + ":" + SSAUtil.paraLocTrans(ret.getSSA(),ret.getCGNode(), ret.getParaLoc()));
							System.out.println("line number" + ":" + IRUtil.getSourceLineNumberFromSSA(ret.getCGNode(),ret.getSSA()));
							System.out.println("++++++++++++++++++++++++++++++++++++++++++++++++");*/
						}		
						System.out.println("size of manually checking" + needCkPoint.size());
						Statistic.writeToFileRawEnd(ps_console);
						System.err.println("finish processsing one benchmark");
						/*if(recorder != null)
							recorder.close();
						if(recorder2 != null)
							recorder.close();
						if(recorder3 != null)
							recorder.close();*/
						long estimatedTime = System.currentTimeMillis() - startTime;
						total_time += estimatedTime;
						System.out.println("size of outer buff " + outPoint);
						System.out.println("size of not need manually checking " + notNeedCheck);
				    }
				}
			}

		}
		System.err.println("analysis time is:(s)" + total_time);
		System.err.println("finish processing totally");
	}
}



