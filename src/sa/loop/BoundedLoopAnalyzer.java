package sa.loop;

import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.ibm.wala.cfg.Util;
import com.ibm.wala.core.tests.callGraph.CallGraphTestUtil;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilder;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.slicer.Slicer;
import com.ibm.wala.ipa.slicer.Slicer.ControlDependenceOptions;
import com.ibm.wala.ipa.slicer.Slicer.DataDependenceOptions;
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.ipa.slicer.thin.ThinSlicer;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSACFG;
import com.ibm.wala.ssa.SSAConditionalBranchInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.Descriptor;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.strings.Atom;

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
import sa.loopsize.BackwardSlicing;
import sa.loopsize.FieldInst;
import sa.loopsize.LabelledSSA;
import sa.loopsize.LoopCond;
import sa.loopsize.MyPair;
import sa.loopsize.MyTriple;
import sa.loopsize.OptCallChain;
import sa.loopsize.ParameterInst;
import sa.loopsize.ProcessUnit;
import sa.loopsize.RetInst;
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

	public BoundedLoopAnalyzer(WalaAnalyzer walaAnalyzer, LoopAnalyzer loopAnalyzer, CGNodeList cgNodeList,
			String proDir) {
		this.walaAnalyzer = walaAnalyzer;
		this.cg = this.walaAnalyzer.getCallGraph();
		this.outputDir = this.walaAnalyzer.getTargetDirPath();
		this.loopAnalyzer = loopAnalyzer;
		this.cgNodeList = cgNodeList;
		// others
		this.iolooputil = new TCOpUtil(this.walaAnalyzer.getTargetDirPath());
		this.projectDir = proDir;
	}

	// Please call doWork() manually
	public void doWork() throws IllegalArgumentException, CancelException {
		System.out.println("\nZC - INFO - BoundedLoopAnalyzer: doWork...");

		findBoundedLoops();
		// statistic();
		// printResultStatus();
		// analysisPath();
		// final_statistic();
	}

	/*****************************************************************************************************
	 * Find bounded loops
	 * 
	 * @throws CancelException
	 * @throws IllegalArgumentException
	 *****************************************************************************************************/

	private void findBoundedLoops() throws IllegalArgumentException, CancelException {
		System.out.println("JX - INFO - BoundedLoopAnalyzer: findBoundedLoops...");

		for (CGNodeInfo cgNodeInfo : loopAnalyzer.getLoopCGNodes()) {
			for (LoopInfo loop : cgNodeInfo.getLoops()) {
				// while true loop
				

			} // loop - LoopInfo
		}

	}

	private boolean checkWhileTure(LoopInfo loop) {
		if (loop.getConditionSSA() != null) {
			return false;
		} else if (LoopCond.isWhileTrueCase(loop.getBeginBasicBlock(), loop.getCGNode())) {
			System.out.println("it's while true case, please manually check it");
			System.out.println(loop);
			try {
				this.walaAnalyzer.viewIR(loop.getCGNode().getIR());
			} catch (WalaException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			System.out.println("finish processing");
			return true;
		} else {
			System.out.println("unknown loop type...");
			System.out.println(loop);
			System.out.println("finish processing");
			return true;
		}
	}
	
	
	
	public void walaSlice(LoopInfo loop) throws IllegalArgumentException, CancelException {
		if (checkWhileTure(loop)) {
			loop.whileTrue = true;
			return;
		}
		CallGraph cg = this.walaAnalyzer.getCallGraph();
		PointerAnalysis pa = this.walaAnalyzer.getPointerA();

		// find seed statement
		System.out.println(loop.getConditionSSA().toString());
		//Statement statement = findCallTo(findMethod(cg,"processReport","BlockManager"),"processReport");
		Statement statement = new com.ibm.wala.ipa.slicer.NormalStatement(loop.getCGNode(),loop.getBeginBasicBlock().getLastInstructionIndex());
				

		Collection<Statement> slice;

		// context-sensitive traditional slice
		slice = Slicer.computeBackwardSlice(statement, cg, pa);
		dumpSlice(slice);

		// context-sensitive thin slice
		slice = Slicer.computeBackwardSlice(statement, cg, pa, DataDependenceOptions.NO_BASE_PTRS,
				ControlDependenceOptions.NONE);
		dumpSlice(slice);

		// context-insensitive slice
		ThinSlicer ts = new ThinSlicer(cg, pa);
		slice = ts.computeBackwardThinSlice(statement);
		dumpSlice(slice);
	}

	public CGNode findMethod(CallGraph cg, String Name, String methodCLass) {
		if (Name.equals(null) && methodCLass.equals(null))
			return null;
		Atom name = Atom.findOrCreateUnicodeAtom(Name);
		for (Iterator<? extends CGNode> it = cg.iterator(); it.hasNext();) {
			CGNode n = it.next();
			if (n.getMethod().getName().equals(name)
					&& n.getMethod().getDeclaringClass().getName().toString().equals(methodCLass)) {
				return n;
			}
		}
		Assertions.UNREACHABLE("Failed to find method " + name);
		return null;
	}

	public static Statement findCallTo(CGNode n, String methodName) {
		IR ir = n.getIR();
		for (Iterator<SSAInstruction> it = ir.iterateAllInstructions(); it.hasNext();) {
			SSAInstruction s = it.next();
			if (s instanceof com.ibm.wala.ssa.SSAAbstractInvokeInstruction) {
				com.ibm.wala.ssa.SSAAbstractInvokeInstruction call = (com.ibm.wala.ssa.SSAAbstractInvokeInstruction) s;
				if (call.getCallSite().getDeclaredTarget().getName().toString().equals(methodName)) {
					com.ibm.wala.util.intset.IntSet indices = ir.getCallInstructionIndices(call.getCallSite());
					com.ibm.wala.util.debug.Assertions.productionAssertion(indices.size() == 1,
							"expected 1 but got " + indices.size());
					return new com.ibm.wala.ipa.slicer.NormalStatement(n, indices.intIterator().next());
				}
			}
		}
		Assertions.UNREACHABLE("failed to find call to " + methodName + " in " + n);
		return null;
	}

	public static void dumpSlice(Collection<Statement> slice) {
		for (Statement s : slice) {
			System.err.println(s);
		}
	}
}
