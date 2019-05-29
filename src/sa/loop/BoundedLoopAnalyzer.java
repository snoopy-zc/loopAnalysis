package sa.loop;

import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

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
import com.ibm.wala.ssa.SSABinaryOpInstruction;
import com.ibm.wala.ssa.SSACFG;
import com.ibm.wala.ssa.SSACFG.BasicBlock;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.ssa.SSAPutInstruction;
import com.ibm.wala.ssa.SymbolTable;
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
				
				analysisLoop(loop);
				
			} // loop - LoopInfo
		}

		System.out.println("ZC - INFO - Loop Info list...");
		for (CGNodeInfo cgNodeInfo : loopAnalyzer.getLoopCGNodes()) {
			for (LoopInfo loop : cgNodeInfo.getLoops()) {
				// while true loop
				if (loop.whileTrue) {
					System.out.println(loop);
					System.out.println(loop.conditional_branch_block);
				}

			} // loop - LoopInfo
		}
	}

	public int analysisLoop(LoopInfo loop) throws IllegalArgumentException, CancelException, WalaException {

		// check if while(true) loop
		if (cheackWhileTrue(loop)) {
			System.out.println("While(true) loop - " + loop);
			whiletrueAnalisys(loop);
		}
		
		Collection<Statement> slice = walaSlice(loop);
		dumpSlice(slice);
		
		for(Statement s : slice) {
			statementAnalysis(s);
		}

		// TODO

		return 0;

	}

	public void whiletrueAnalisys(LoopInfo loop) { //this method can be apply to all loop not only while-true
		// TODO Auto-generated method stub
		IR ir = loop.getCGNode().getIR();
		SSACFG cfg = ir.getControlFlowGraph();

		for (int bindex = loop.getBeginBasicBlockNumber(); bindex <= loop.getEndBasicBlockNumber(); bindex++) {
			BasicBlock bb = cfg.getBasicBlock(bindex);						
			Collection<ISSABasicBlock> suc = cfg.getNormalSuccessors(bb); // find the succ bb without considering
																			// exception flow
			for (ISSABasicBlock b : suc) {
				if (b.getNumber() > loop.getEndBasicBlockNumber()) { // find block that skip out 
					//trace back to the conditional branch until the begin_block
					Set<Integer> CBB = traceBackCBB(cfg,bb.getNumber(),loop.getBeginBasicBlockNumber());
					loop.conditional_branch_block.addAll(CBB);									
				}
			}
		}
	}
	//TODO to test it
	public Set<Integer> traceBackCBB(SSACFG cfg, int outBB, int beginBB){

		BasicBlock bb = cfg.getBasicBlock(outBB);
		Set<Integer> result = new HashSet<Integer>();
				
		if(cfg.getNormalSuccessors(bb).size()>1)//the current node is branch to skip out
			result.add(outBB);
		
		if(outBB <= beginBB)
			return result;
		
		Collection<ISSABasicBlock> bbs = cfg.getNormalPredecessors(bb);
		for(ISSABasicBlock pb: bbs) {
			if(cfg.getNormalSuccessors(pb).size()>1)//the current node is branch to skip out
				result.add(pb.getNumber());
			result.addAll(traceBackCBB(cfg,pb.getNumber(),beginBB));
		}		
		return result;
	}


	public boolean cheackWhileTrue(LoopInfo loop) {
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
		return loop.whileTrue;
		// this method can not detect the following situation, but it maybe doesn't
		// matter : the final block is break as one branch of if-else structure, and the
		// ir-graph is same as do-while structure
		/*
		 * while (true) { System.out.println("while - true..."); 
		 * if(a[0] > 0) { a[0] ++; break; } }
		 */

	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public Collection<Statement> walaSlice(LoopInfo loop)
			throws IllegalArgumentException, CancelException, WalaException {
		if (loop.whileTrue) // don not slicing for while true loop
			return new HashSet(); // TODO analysis the if-break condition
		CallGraph cg = this.walaAnalyzer.getCallGraph();
		PointerAnalysis pa = this.walaAnalyzer.getPointerA();

		// for test
		// if (loop.getCGNode().toString().indexOf("testFor1") >= 0)
		// this.walaAnalyzer.viewIR(loop.getCGNode().getIR());

		System.out.println("ZC - INFO - walaSlice - " + loop);
		System.out.println(loop.getConditionSSA().toString());

		// find seed statement
		Statement statement = new NormalStatement(loop.getCGNode(),
				loop.getBeginBasicBlock().getLastInstructionIndex());

		Collection<Statement> slice;

		// context-insensitive slice -- we haven't try //TODO we can have a try
		// ThinSlicer ts = new ThinSlicer(cg, pa);
		// slice = ts.computeBackwardThinSlice(statement);
		// dumpSlice(slice);

		// context-sensitive thin slice
		slice = Slicer.computeBackwardSlice(statement, cg, pa, DataDependenceOptions.NO_BASE_PTRS,
				ControlDependenceOptions.NONE);
		// Slicer.computeBackwardSlice(SDG sdg, Statement s)
		// Slicer.computeBackwardSlice(SDG sdg, Collection<Statement> ss)
		// Slicer.computeBackwardSlice(Statement s, CallGraph cg,
		// PointerAnalysis<InstanceKey> pointerAnalysis) // context-sensitive
		// traditional slice -- it works very slowly or seems dosn't work
		// Slicer.computeBackwardSlice(Statement s, CallGraph cg,
		// PointerAnalysis<InstanceKey> pa, DataDependenceOptions dOptions,
		// ControlDependenceOptions cOptions)

		// DataDependenceOptions dOptions =
		// Slicer.DataDependenceOptions.NO_BASE_PTRS;
		// DataDependenceOptions dOptions =
		// Slicer.DataDependenceOptions.NO_BASE_NO_HEAP;
		// DataDependenceOptions dOptions =
		// Slicer.DataDependenceOptions.NO_BASE_NO_EXCEPTIONS;
		// DataDependenceOptions dOptions =
		// Slicer.DataDependenceOptions.NO_BASE_NO_HEAP_NO_EXCEPTIONS;
		// DataDependenceOptions dOptions =
		// Slicer.DataDependenceOptions.NO_HEAP;
		// DataDependenceOptions dOptions =
		// Slicer.DataDependenceOptions.NO_HEAP_NO_EXCEPTIONS;
		// DataDependenceOptions dOptions =
		// Slicer.DataDependenceOptions.NO_EXCEPTIONS;
		// DataDependenceOptions dOptions =
		// Slicer.DataDependenceOptions.REFLECTION;
		// DataDependenceOptions dOptions = Slicer.DataDependenceOptions.NONE;
		// ControlDependenceOptions cOptions =
		// Slicer.ControlDependenceOptions.NONE;
		return slice;
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

	public Statement findCallTo(CGNode n, String methodName) {
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

	public void dumpSlice(Collection<Statement> slice) {
		for (Statement s : slice) {
			if (s.toString().indexOf("< Primordial,") >= 0) continue;
			System.out.println(s);
		}
	}

	public void statementAnalysis(Statement s) {
		if (s.toString().indexOf("< Primordial,") >= 0)
			return;
		IR ir = s.getNode().getIR();
		SymbolTable st = ir.getSymbolTable();
		System.out.println("#" + s);
		/*
		 * switch (s.getKind()) { // case CATCH: case HEAP_PARAM_CALLEE: case
		 * HEAP_PARAM_CALLER: case HEAP_RET_CALLEE: case HEAP_RET_CALLER: HeapStatement
		 * h = (HeapStatement) s; System.out.println(h.getKind() + "\\n" + h.getNode() +
		 * "\\n" + h.getLocation()); return; case NORMAL: NormalStatement n =
		 * (NormalStatement) s; System.out.println(n.getInstruction().toString(st) +
		 * "\\n" + n.getNode().getMethod().getSignature()); //*
		 * System.err.println(n.getInstruction().toString(s.getNode().getIR().
		 * getSymbolTable())); System.err.println(n.getInstruction().getNumberOfUses());
		 * System.err.println(n.getInstruction().getNumberOfDefs());
		 * System.err.println(n.getInstruction());
		 *
		 * return; case PARAM_CALLEE: ParamCallee paramCallee = (ParamCallee) s;
		 * System.out.println( s.getKind() + " " + paramCallee.getValueNumber() + "\\n"
		 * + s.getNode().getMethod().getName()); return; case PARAM_CALLER: ParamCaller
		 * paramCaller = (ParamCaller) s; System.out.println(paramCaller.getKind() + " "
		 * + paramCaller.getValueNumber() + "\\n" + s.getNode().getMethod().getName() +
		 * "\\n" +
		 * paramCaller.getInstruction().getCallSite().getDeclaredTarget().getName());
		 * 
		 * System.out.println(paramCaller.getNode().getIR().getSymbolTable().getValue(
		 * paramCaller.getValueNumber())); return; case EXC_RET_CALLEE: case
		 * EXC_RET_CALLER: case NORMAL_RET_CALLEE: case NORMAL_RET_CALLER: case PHI: //
		 * case PI: default: break; } System.out.println("! Unanalysis statement - " +
		 * s.toString());
		 */
	}

	public void parseNormalStatement(NormalStatement s) {
		SSAInstruction inst = s.getInstruction();

		if (inst instanceof SSAInvokeInstruction) {

		}
		if (inst instanceof SSAGetInstruction) {

		}
		if (inst instanceof SSAPutInstruction) {

		}
		if (inst instanceof SSABinaryOpInstruction) {

		}
		if (inst instanceof SSANewInstruction) {

		}
	}

}
