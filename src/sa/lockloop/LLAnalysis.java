/*******************************************************************************
 * Copyright (c) 2002 - 2006 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package sa.lockloop;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.HashMap;

import com.benchmark.Benchmarks;

//import org.eclipse.jface.window.ApplicationWindow;

import com.ibm.wala.analysis.typeInference.TypeInference;
import com.ibm.wala.classLoader.IBytecodeMethod;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAArrayReferenceInstruction;
import com.ibm.wala.ssa.SSACFG;
import com.ibm.wala.ssa.SSACheckCastInstruction;
import com.ibm.wala.ssa.SSAFieldAccessInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSALoadMetadataInstruction;
import com.ibm.wala.ssa.SSAMonitorInstruction;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.graph.Acyclic;
import com.ibm.wala.util.intset.IBinaryNaturalRelation;
import com.ibm.wala.util.intset.IntIterator;
import com.ibm.wala.util.intset.IntPair;
import com.ibm.wala.util.io.CommandLine;
import com.system.Timer;

import sa.lock.LockAnalyzer;
import sa.lock.LockInfo;
import sa.lock.LoopingLockAnalyzer;
import sa.lock.LoopingLockInfo;
import sa.loop.TCOpUtil;
import sa.loop.LoopAnalyzer;
import sa.loop.LoopInfo;
import sa.loop.NestedLoopAnalyzer;
import sa.loop.TCLoopAnalyzer;
import sa.loop.TcOperationInfo;
import sa.tcop.LockLoopTcOpAnalyzer;
import sa.tcop.LockingLoopAnalyzer;
import sa.tcop.TcOpAnalyzer;
import sa.tcop.PathInfo;
import sa.wala.IRUtil;
import sa.wala.WalaAnalyzer;
import sa.wala.util.PDFCallGraph;


public class LLAnalysis {
	// dir paths
	String projectDir;  // read from arguments, like "/root/loopAnalysis(/)"   #jx: couldn't obtain automatically, because of many scenarios
	String jarsDir;   // read from arguments, like "/root/loopAnalysis/src/sa/res/MapReduce/hadoop-0.23.3(/)"   
	Timer timer;
  
	// WALA basis
	WalaAnalyzer wala;
	CallGraph cg;
	ClassHierarchy cha;

  
	LockAnalyzer lockAnalyzer;
	LoopAnalyzer loopAnalyzer;
	TCOpUtil iolooputil;
	
	// results
	CGNodeList cgNodeList;
	
	// Target System
	String systemname = null;   // current system's name  
 
  
	// For test
	String functionname_for_test = "org.apache.hadoop.hdfs.DFSOutputStream$DataStreamer$ResponseProcessor.run("; //"RetryCache.waitForCompletion(Lorg/apache/hadoop/ipc/RetryCache$CacheEntry;)"; //"org.apache.hadoop.hdfs.server.balancer.Balancer"; //"Balancer$Source.getBlockList";//"DirectoryScanner.scan"; //"ReadaheadPool.getInstance("; //"BPServiceActor.run("; //"DataNode.runDatanodeDaemon"; //"BPServiceActor.run("; //"BlockPoolManager.startAll"; //"NameNodeRpcServer"; //"BackupNode$BackupNodeRpcServer"; // //".DatanodeProtocolServerSideTranslatorPB"; //"DatanodeProtocolService$BlockingInterface"; //"sendHeartbeat("; //"org.apache.hadoop.hdfs.protocolPB.DatanodeProtocolServerSideTranslatorPB";  //java.util.regex.Matcher.match(";
	int which_functionname_for_test = 1;   //1st? 2nd? 3rd?    //TODO - 0 means ALL, 1 to n means which one respectively
  
  
  
	/*
	public LLAnalysis(WalaAnalyzer walaAnalyzer) {
		this(walaAnalyzer, ".");
	}
	*/
	
	public LLAnalysis(WalaAnalyzer walaAnalyzer, String projectDir, Timer timer) {
		this.wala = walaAnalyzer;
		this.projectDir = projectDir;
		this.jarsDir = wala.getTargetDirPath().toString();
		this.timer = timer;
		// others
		this.cgNodeList = new CGNodeList(this.wala.getCallGraph());
		doWork();
	}
	
  
	public void doWork() {
		System.out.println("\nJX - INFO - LLAnalysis.doWork");
	    try {	     
	    	//timer
	    	//Timer timer = new Timer( Paths.get(projectDir, "src/sa/output/sa-timer.txt") );
	    	//timer.tic("LLAnalysis begin");
	      
			systemname = Benchmarks.resolveSystem(jarsDir);
			System.out.println("JX - DEBUG - system name = " + systemname);
			this.cg = wala.getCallGraph();
			this.cha = wala.getClassHierarchy();

	
			// Lock analysis
			this.lockAnalyzer = new LockAnalyzer(this.wala, this.cgNodeList);
			lockAnalyzer.doWork();
			timer.toc("lockAnalyzer end");

			
			// Loop analysis
			this.loopAnalyzer = new LoopAnalyzer(this.wala, this.cgNodeList);
			loopAnalyzer.doWork();
			timer.toc("loopAnalyzer end");

			
			// loops-containing lock
			LoopingLockAnalyzer loopingLockAnalyzer = new LoopingLockAnalyzer(this.wala, this.lockAnalyzer, this.loopAnalyzer, this.cgNodeList);
			loopingLockAnalyzer.doWork();
			timer.toc("loopingLockAnalyzer end");
			
			// zc - this part is unnecessary, because it has been done in loopingLockAnalyzer()
			// loops-containing loop
			//NestedLoopAnalyzer nestedLoopAnalyzer = new NestedLoopAnalyzer(this.wala, this.loopAnalyzer, this.cgNodeList);
			//nestedLoopAnalyzer.doWork();
			//timer.toc("nestedLoopAnalyzer end");
			
			
			TCLoopAnalyzer tcLoopAnalyzer = new TCLoopAnalyzer(this.wala, this.loopAnalyzer, this.cgNodeList);
			tcLoopAnalyzer.doWork();
			timer.toc("tcLoopAnalyzer end");
			
			// Out of Memory??
			//TcOpAnalyzer tcOpAnalyzer = new TcOpAnalyzer(this.wala, this.lockAnalyzer, this.cgNodeList);
			//tcOpAnalyzer.doWork();
			//timer.toc("tcOpAnalyzer end");
			
			//LockLoopTcOpAnalyzer lockLoopTcOpAnalyzer = new LockLoopTcOpAnalyzer(this.wala, this.lockAnalyzer, this.cgNodeList);
			//lockLoopTcOpAnalyzer.doWork();
			//timer.toc("LockLoopTcOpAnalyzer end");
			
			LockingLoopAnalyzer lockingLoopAnalyzer = new LockingLoopAnalyzer(this.wala, this.lockAnalyzer, this.loopAnalyzer, this.cgNodeList);
			lockingLoopAnalyzer.doWork();
			timer.toc("LockingLoopAnalyzer end");
			
			LockingLoopAnalyzer lockingLoopAnalyzer = new LockingLoopAnalyzer(this.wala, this.lockAnalyzer, this.loopAnalyzer, this.cgNodeList);
			lockingLoopAnalyzer.doWork();
			timer.toc("LockingLoopAnalyzer end");
	
			
			
			
			
			
			//print customized result
			//printResult();						
	      
	    } catch (Exception e) {
	      System.err.println("JX-StackTrace-run-begin");
	      e.printStackTrace();
	      System.err.println("JX-StackTrace-run-end");
	      return ;
	    }
	}
	
	public void printResult() {
		//zc- just for test 

		System.out.println("******************** test result **************************");		

		int cgNodeCnt = 0;
		int lockCnt = 0;
		int loopCnt = 0;
		for(CGNodeInfo cgNodeInfo: this.cgNodeList.values()) {
			boolean flag = false;
			if(cgNodeInfo.hasLocks()) {
				//System.err.println(cgNodeInfo.getCGNode().getMethod());
				//System.err.println(cgNodeInfo.locks);
			}
			if(cgNodeInfo.hasLoops()) {
				//System.err.println(cgNodeInfo.getCGNode().getMethod());
				//System.err.println(cgNodeInfo.loops.size()+"#"+cgNodeInfo.loops);
			}
			if(cgNodeInfo.hasLoopingLocks) { // only can detect the first level
				//System.err.println(cgNodeInfo.getCGNode().getMethod());
				//cgNodeCnt++;
				//System.err.println(cgNodeInfo.looping_locks);
			}
			// test TcLoop
			if(false&&cgNodeInfo.hasLoops()) {
				for(LoopInfo loop : cgNodeInfo.getLoops()) {
					if (loop.numOfTcOperations_recusively > 0) {
						loopCnt++;
						flag = true;
						for(TcOperationInfo tcop : loop.tcOperations_recusively_info) {
							if(true||tcop.toString().indexOf("java/io/DataOutputStream")>=0) {
								System.err.println(tcop);
							}
						}
					}
				}
				if(flag) {
					cgNodeCnt++;
				}
			}
			//test print some function content
			if(false&&cgNodeInfo.getCGNode().getMethod().toString().indexOf("HRegion, internalFlushcache(")>=0) {
				System.err.println("-------------"+cgNodeInfo.getCGNode().getMethod());
				System.err.println(cgNodeInfo.tcOperations);
				System.err.println(cgNodeInfo.tcOperations_recusively);				
				//System.err.println(cgNodeInfo.instructions);
				for(SSAInstruction ssa : cgNodeInfo.getCGNode().getIR().getInstructions())
					System.err.println(ssa);
			}
		}
		System.out.println("#cgNodes = " + cgNodeCnt + " containing loopingLocks(" +lockCnt+ ") with TCLoop(" +loopCnt+ ")");
		System.out.println("******************** test end **************************");
	}  
}










