package sa.loop;

import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.slicer.HeapStatement;
import com.ibm.wala.ipa.slicer.NormalStatement;
import com.ibm.wala.ipa.slicer.ParamCallee;
import com.ibm.wala.ipa.slicer.ParamCaller;
import com.ibm.wala.ipa.slicer.Slicer;
import com.ibm.wala.ipa.slicer.Slicer.ControlDependenceOptions;
import com.ibm.wala.ipa.slicer.Slicer.DataDependenceOptions;
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.ipa.slicer.thin.ThinSlicer;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSACFG;
import com.ibm.wala.ssa.SSACFG.BasicBlock;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.util.strings.Atom;

import sa.lockloop.CGNodeInfo;
import sa.lockloop.CGNodeList;
import sa.loop.LoopAnalyzer;
import sa.loop.LoopInfo;
import sa.loop.TCOpUtil;
import sa.loopsize.LoopCond;
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

	String projectDir;

	public BoundedLoopAnalyzer(WalaAnalyzer walaAnalyzer, LoopAnalyzer loopAnalyzer, CGNodeList cgNodeList,
			String proDir) {
		this.walaAnalyzer = walaAnalyzer;
		this.cg = this.walaAnalyzer.getCallGraph();
		this.outputDir = this.walaAnalyzer.getTargetDirPath();
		this.loopAnalyzer = loopAnalyzer;
		this.cgNodeList = cgNodeList;
		this.projectDir = proDir;
	}

	// Please call doWork() manually
	public void doWork() throws IllegalArgumentException, CancelException, WalaException {
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
	 * @throws WalaException
	 *****************************************************************************************************/

	private void findBoundedLoops() throws IllegalArgumentException, CancelException, WalaException {
		System.out.println("JX - INFO - BoundedLoopAnalyzer: findBoundedLoops...");

		for (CGNodeInfo cgNodeInfo : loopAnalyzer.getLoopCGNodes()) {
			for (LoopInfo loop : cgNodeInfo.getLoops()) {
				// while true loop

				analysisLoop(loop);

				walaSlice(loop);

			} // loop - LoopInfo
		}

		System.out.println("ZC - INFO - Loop Info list...");
		for (CGNodeInfo cgNodeInfo : loopAnalyzer.getLoopCGNodes()) {
			for (LoopInfo loop : cgNodeInfo.getLoops()) {
				// while true loop
				if (loop.whileTrue) {
					System.out.println(loop);
				}

			} // loop - LoopInfo
		}
	}

	public int analysisLoop(LoopInfo loop) {

		// System.out.println(loop);
		IR ir = loop.getCGNode().getIR();
		SSACFG cfg = ir.getControlFlowGraph();

		for (int bindex = loop.getBeginBasicBlockNumber(); bindex <= loop.getEndBasicBlockNumber(); bindex++) {
			BasicBlock bb = cfg.getBasicBlock(bindex);
			Collection<ISSABasicBlock> suc = cfg.getNormalSuccessors(bb); // find the succ bb without considering
																			// exception flow
			if (suc.size() > 1) // conditional branch exists
				for (ISSABasicBlock b : suc) {
					if (b.getNumber() > loop.getEndBasicBlockNumber()) { // find conditional branch skip out
						loop.whileTrue = false;
						loop.conditional_branch_block.add(bindex);
					}
					// System.out.println("BB id:" + bb.getNumber());
					// System.out.println(suc.toString());
				}
		}
		return 0;
	}

	// using wala slicing feature, but lead to memory error..

	public void walaSlice(LoopInfo loop) throws IllegalArgumentException, CancelException, WalaException {
		if (loop.whileTrue) // don not slicing for while true loop
			return;
		CallGraph cg = this.walaAnalyzer.getCallGraph();
		PointerAnalysis pa = this.walaAnalyzer.getPointerA();

		if (loop.getCGNode().toString().indexOf("run") >= 0)
			this.walaAnalyzer.viewIR(loop.getCGNode().getIR());

		// find seed statement
		System.out.println("ZC - INFO - walaSlice - " + loop);
		System.out.println(loop.getConditionSSA().toString());
		// Statement statement =
		// findCallTo(findMethod(cg,"processReport","BlockManager"),"processReport");
		Statement statement = new com.ibm.wala.ipa.slicer.NormalStatement(loop.getCGNode(),
				loop.getBeginBasicBlock().getLastInstructionIndex());

		Collection<Statement> slice;

		// context-sensitive traditional slice -- it works very slowly
		// slice = Slicer.computeBackwardSlice(statement, cg, pa);
		// dumpSlice(slice);

		// context-sensitive thin slice
		slice = Slicer.computeBackwardSlice(statement, cg, pa, DataDependenceOptions.NO_BASE_PTRS,
				ControlDependenceOptions.NONE);
		dumpSlice(slice);
		// Slicer.computeBackwardSlice(SDG sdg, Statement s)
		// Slicer.computeBackwardSlice(SDG sdg, Collection<Statement> ss)
		// Slicer.computeBackwardSlice(Statement s, CallGraph cg,
		// PointerAnalysis<InstanceKey> pointerAnalysis)
		// Slicer.computeBackwardSlice(Statement s, CallGraph cg,
		// PointerAnalysis<InstanceKey> pa, DataDependenceOptions dOptions,
		// ControlDependenceOptions cOptions)

		// context-insensitive slice
		// ThinSlicer ts = new ThinSlicer(cg, pa);
		// slice = ts.computeBackwardThinSlice(statement);
		// dumpSlice(slice);
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
			if (s.toString().indexOf("< Primordial,") >= 0)
				continue;
			System.out.println(Statement2S(s));

			if (s.toString().indexOf("PARAM_CALLER:Node: < Application, LTest, run()V > ") >= 0) {
				System.out.println("v16 = " + s.getNode().getIR().getSymbolTable().getValue(16));
				System.out.println("v17 = " + s.getNode().getIR().getSymbolTable().getValue(17));
			}

		}		
	}
	
	public static String Statement2S(Statement s) {
		switch (s.getKind()) {
		
		case HEAP_PARAM_CALLEE:
		case HEAP_PARAM_CALLER:
		case HEAP_RET_CALLEE:
		case HEAP_RET_CALLER:
			HeapStatement h = (HeapStatement) s;
			return s.getKind() + "\\n" + h.getNode() + "\\n" + h.getLocation();
		case NORMAL:
			NormalStatement n = (NormalStatement) s;
			return n.getInstruction() + "\\n" + n.getNode().getMethod().getSignature();
		case PARAM_CALLEE:
			ParamCallee paramCallee = (ParamCallee) s;
			return s.getKind() + " " + paramCallee.getValueNumber() + "\\n" + s.getNode().getMethod().getName();
		case PARAM_CALLER:
			ParamCaller paramCaller = (ParamCaller) s;
			return s.getKind() + " " + paramCaller.getValueNumber() + "\\n" + s.getNode().getMethod().getName()
					+ "\\n" + paramCaller.getInstruction().getCallSite().getDeclaredTarget().getName();
		case EXC_RET_CALLEE:
		case EXC_RET_CALLER:
		case NORMAL_RET_CALLEE:
		case NORMAL_RET_CALLER:
		case PHI:
		default:
			break;
		}
		return s.toString();
	}
	
}
