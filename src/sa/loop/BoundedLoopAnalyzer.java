package sa.loop;

import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSACFG;
import com.ibm.wala.ssa.SSAConditionalBranchInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.types.ClassLoaderReference;

import javafx.util.Pair;
import sa.lock.LockAnalyzer;
import sa.lock.LockInfo;
import sa.lock.LoopingLockInfo;
import sa.lockloop.CGNodeInfo;
import sa.lockloop.CGNodeList;
import sa.lockloop.InstructionInfo;
import sa.loop.LoopAnalyzer;
import sa.loop.LoopInfo;
import sa.loop.TCOpUtil;
import sa.loop.TcOperationInfo;
import sa.loopsize.LoopCond;
import sa.loopsize.MyPair;
import sa.loopsize.MyTriple;
import sa.loopsize.ProcessUnit;
import sa.loopsize.SSAUtil;
import sa.loopsize.Statistic;
import sa.wala.CGNodeUtil;
import sa.wala.IRUtil;
import sa.wala.WalaAnalyzer;

public class BoundedLoopAnalyzer {
	
	// wala
	WalaAnalyzer walaAnalyzer;
	CallGraph cg;
	Path outputDir;
	// database
	CGNodeList cgNodeList = null;
	// loop
	LoopAnalyzer loopAnalyzer;
	
	TCOpUtil iolooputil = null;
	
	String projectDir;
	
	
	
	
	public BoundedLoopAnalyzer(WalaAnalyzer walaAnalyzer, LoopAnalyzer loopAnalyzer, CGNodeList cgNodeList, String proDir) {
		this.walaAnalyzer = walaAnalyzer;
		this.cg = this.walaAnalyzer.getCallGraph();
		this.outputDir = this.walaAnalyzer.getTargetDirPath();
		this.loopAnalyzer = loopAnalyzer;
		this.cgNodeList = cgNodeList;
		//others
		this.iolooputil = new TCOpUtil( this.walaAnalyzer.getTargetDirPath() );
		this.projectDir = proDir;
	}
	
	// Please call doWork() manually
	public void doWork() {
		System.out.println("\nZC - INFO - BoundedLoopAnalyzer: doWork...");
		
		findBoundedLoops();		
		//statistic();
		//printResultStatus();
		//analysisPath();
		//final_statistic();
	}
	
	
	/*****************************************************************************************************
	 * Find bounded loops
	 *****************************************************************************************************/
	
	private void findBoundedLoops() {
		System.out.println("JX - INFO - BoundedLoopAnalyzer: findBoundedLoops...");
		
		for (CGNodeInfo cgNodeInfo: loopAnalyzer.getLoopCGNodes()) {
			for(LoopInfo loop: cgNodeInfo.getLoops()) {
				// while true loop 
				if(checkWhileTure(loop)) {
					loop.whileTrue = true;
					continue;
				}
				// regular analysis
				HashSet<MyPair> whoHashRun = new HashSet<MyPair>();				
				HashSet<MyTriple> bList = new HashSet<MyTriple>();	
				String modelLibLoc = Paths.get(projectDir,"blank.txt").toString();
				ProcessUnit unit = new ProcessUnit(this.cg,
													loop.getCGNode(),
													loop.getConditionSSA(),
													true,
													0,//level
													-1,
													this.loopAnalyzer,
													modelLibLoc,
													null,
													whoHashRun);
				SSAUtil.isBoundedSearch(unit,this.loopAnalyzer, bList,modelLibLoc,whoHashRun);				
				
				System.out.println("size of result buff" + bList.size());
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
				System.out.println("size of outer buff " + outPoint);
				System.out.println("size of not need manually checking " + notNeedCheck);
				System.out.println("size of conf bounded " + confRelated);
				System.out.println("size of constant bounded " + constantRelated);
				System.out.println("size of manually checking " + (outPoint - notNeedCheck));
				System.out.println("size of maybe RPC " + maybeRPC);
				System.out.println("size of bounded array/collection" + isCABounded);
				
				System.out.println("print the point we need check");
				
				for(MyTriple ret : needCkPoint){
					System.out.println(ret.getCGNode().getMethod().getDeclaringClass().getName().toString() + " " + 
							ret.getCGNode().getMethod().getName().toString() + " " +
							IRUtil.getSourceLineNumberFromSSA(ret.getCGNode(),ret.getSSA()) + " " + this.walaAnalyzer.getTargetDirPath().toString() + " " + ret.getParaLoc());
					/*System.out.println("++++++++++++++++++++++++++++++++++++++++++++++++");
					System.out.println("class name" + ":" + ret.getCGNode().getMethod().getDeclaringClass().getName().toString());
					System.out.println("method name" + ":" + ret.getCGNode().getMethod().getName().toString());
					System.out.println("SSAInstruction" + ":" + ret.getSSA().toString());
					System.out.println("paraLoc" + ":" + SSAUtil.paraLocTrans(ret.getSSA(),ret.getCGNode(), ret.getParaLoc()));
					System.out.println("line number" + ":" + IRUtil.getSourceLineNumberFromSSA(ret.getCGNode(),ret.getSSA()));
					System.out.println("++++++++++++++++++++++++++++++++++++++++++++++++");*/
				}		
				System.out.println("size of manually checking" + needCkPoint.size());				
			}//loop - LoopInfo
		}
		
	}
	
	private boolean checkWhileTure(LoopInfo loop) {
		if(loop.getConditionSSA() != null) {
			return false;
		}else if(LoopCond.isWhileTrueCase(loop.getBeginBasicBlock(),loop.getCGNode())) {
			System.out.println("it's while true case, please manually check it");
			System.out.println(loop);
			System.out.println("finish processing");
			return true;
		}else {
			System.out.println("unknown loop type...");
			System.out.println(loop);
			System.out.println("finish processing");
			return true;
		}
	}	
}
