package sa.loop;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.slicer.ExceptionalReturnCaller;
import com.ibm.wala.ipa.slicer.HeapStatement;
import com.ibm.wala.ipa.slicer.NormalReturnCaller;
import com.ibm.wala.ipa.slicer.NormalStatement;
import com.ibm.wala.ipa.slicer.ParamCallee;
import com.ibm.wala.ipa.slicer.ParamCaller;
import com.ibm.wala.ipa.slicer.PhiStatement;
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.ipa.slicer.thin.ThinSlicer;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.ReflectiveMemberAccess;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAAbstractThrowInstruction;
import com.ibm.wala.ssa.SSAAbstractUnaryInstruction;
import com.ibm.wala.ssa.SSAAddressOfInstruction;
import com.ibm.wala.ssa.SSAArrayLengthInstruction;
import com.ibm.wala.ssa.SSAArrayReferenceInstruction;
import com.ibm.wala.ssa.SSABinaryOpInstruction;
import com.ibm.wala.ssa.SSACFG;
import com.ibm.wala.ssa.SSACFG.BasicBlock;
import com.ibm.wala.ssa.SSACheckCastInstruction;
import com.ibm.wala.ssa.SSAComparisonInstruction;
import com.ibm.wala.ssa.SSAConditionalBranchInstruction;
import com.ibm.wala.ssa.SSAConversionInstruction;
import com.ibm.wala.ssa.SSAFieldAccessInstruction;
import com.ibm.wala.ssa.SSAGetCaughtExceptionInstruction;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SSAGotoInstruction;
import com.ibm.wala.ssa.SSAInstanceofInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSALoadMetadataInstruction;
import com.ibm.wala.ssa.SSAMonitorInstruction;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.ssa.SSAPhiInstruction;
import com.ibm.wala.ssa.SSAPutInstruction;
import com.ibm.wala.ssa.SSAReturnInstruction;
import com.ibm.wala.ssa.SSAStoreIndirectInstruction;
import com.ibm.wala.ssa.SSASwitchInstruction;
import com.ibm.wala.ssa.SymbolTable;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.strings.Atom;

import javafx.util.Pair;

import sa.lockloop.CGNodeInfo;
import sa.lockloop.CGNodeList;
import sa.loop.LoopAnalyzer;
import sa.loop.LoopInfo;
import sa.loop.TCOpUtil;
import sa.wala.IRUtil;
import sa.wala.WalaAnalyzer;

public class BoundedLoopAnalyzer {

	// wala
	WalaAnalyzer walaAnalyzer;
	CallGraph cg;
	Path outputDir;
	// database
	// CGNodeList cgNodeList = null;
	// loop
	// LoopAnalyzer loopAnalyzer;
	TCOpUtil iolooputil;
	Collection<LoopInfo> loops = new HashSet<LoopInfo>(); // entry in CGNodeList

	String projectDir;

	public BoundedLoopAnalyzer(WalaAnalyzer walaAnalyzer, Collection<LoopInfo> collection, String proDir) {
		this.walaAnalyzer = walaAnalyzer;
		this.cg = this.walaAnalyzer.getCallGraph();
		this.outputDir = this.walaAnalyzer.getTargetDirPath();
//		this.loopAnalyzer = loopAnalyzer;
//		this.cgNodeList = cgNodeList;
		this.loops = collection;
		this.projectDir = proDir;
		this.iolooputil = new TCOpUtil(this.walaAnalyzer.getTargetDirPath());

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
		//System.out.println("JX - INFO - BoundedLoopAnalyzer: findBoundedLoops...");

		for (LoopInfo loop : this.loops) {
			analysisLoop(loop);
		} // loop - LoopInfo

		System.out.println("ZC - INFO - Loop Info list...");
		for (LoopInfo loop : this.loops) {
			// while true loop
			if (loop.whileTrue) {
				System.out.println(loop);
				System.out.println(loop.conditional_branch_block);
			}
		} // loop - LoopInfo

		// for test
//		this.walaAnalyzer.viewIR(findMethod(this.walaAnalyzer.getCallGraph(), "testFor1", "LTest").getIR());
	}

	public int analysisLoop(LoopInfo loop) throws IllegalArgumentException, CancelException, WalaException {

		System.out.println("\n\n ZC - INFO - walaSlice - " + loop);

		// check if while(true) loop
		if (cheackWhileTrue(loop)) {
			System.out.println("While(true) loop - " + loop);
			whiletrueAnalisys(loop);
		}

		if (loop.getConditionSSA() == null) // some while true loop do not have branch instruction, instead using
											// exception
			return 0;

		System.out.println(loop.getConditionSSA().toString());

		Collection<Statement> slice = walaSlice(loop);

		for (Statement s : slice) {
			statementAnalysis(s, loop);
		}

		// TODO
		if (loop.IOs.size() > 0) {
			System.out.println("Unbounded!");
//			for (Statement s : loop.IOs)
//				System.out.println(s);
		} else {
			System.out.println("Bounded..");
		}
		
		if(true) return 0;
		
//show immediate value table
//		System.out.println("###def table");
//		for (Pair<CGNode, Integer> e : loop.V_def) {
//			System.out.println(e.getKey() + " - " + e.getValue());
//		}

		System.out.println("###undef table");

		HashMap<Pair<CGNode, Integer>, Collection<Statement>> ht = new HashMap<Pair<CGNode, Integer>, Collection<Statement>>();

		for (Pair<CGNode, Integer> e : loop.V_nul) {
			ht.put(e, look4Value(slice, e.getKey(), e.getValue()));
		}

		loop.V_nul_ht = ht;

		for (Pair<CGNode, Integer> e : loop.V_nul) {
			SymbolTable st = e.getKey().getIR().getSymbolTable();
			//maybe the value cannot be found in getSymbolTable
			//java.lang.IllegalArgumentException: Invalid value number -1
			if (st.getValue(e.getValue()) == null) {
				System.out.println("Variable need to check manually: " + e.getKey() + " - v" + e.getValue() + " - "
						+ st.getValue(e.getValue()) + " -> " + ht.get(e));
			} else if (!st.getValue(e.getValue()).toString().startsWith("#")) {
				System.out.println("Variable need to check manually: " + e.getKey() + " - v" + e.getValue() + " - "
						+ st.getValue(e.getValue()) + " -> " + ht.get(e));
			}
		}

		return 0;

	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public Collection<Statement> walaSlice(LoopInfo loop)
			throws IllegalArgumentException, CancelException, WalaException {
		if (loop.whileTrue) // don not slicing for while true loop
			return new HashSet(); // TODO analysis the if-break condition
		CallGraph cg = this.walaAnalyzer.getCallGraph();
		PointerAnalysis pa = this.walaAnalyzer.getPointerA();

		// find seed statement
		Statement statement = new NormalStatement(loop.getCGNode(),
				IRUtil.getSSAIndex(loop.getCGNode(), loop.getConditionSSA()));

		Collection<Statement> slice;

		// context-insensitive slice (not configurable!)
		ThinSlicer ts = new ThinSlicer(cg, pa);
		slice = ts.computeBackwardThinSlice(statement);
		List<Statement> slices = new ArrayList<Statement>(slice);
		Collections.reverse(slices);
		return slices;

		// context-sensitive thin slice
//		slice = Slicer.computeBackwardSlice(statement, cg, pa, DataDependenceOptions.NO_HEAP_NO_EXCEPTIONS,
//				ControlDependenceOptions.NONE);
//		return slice;

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
		// DataDependenceOptions dOptions =
		// Slicer.DataDependenceOptions.NONE;
		// ControlDependenceOptions cOptions =
		// Slicer.ControlDependenceOptions.NONE;
	}

	public void whiletrueAnalisys(LoopInfo loop) { // this method can be apply to all loop not only while-true
		// TODO Auto-generated method stub
		IR ir = loop.getCGNode().getIR();
		SSACFG cfg = ir.getControlFlowGraph();

		for (int bindex = loop.getBeginBasicBlockNumber(); bindex <= loop.getEndBasicBlockNumber(); bindex++) {
			BasicBlock bb = cfg.getBasicBlock(bindex);
			Collection<ISSABasicBlock> suc = cfg.getNormalSuccessors(bb); // find the succ bb without considering
																			// exception flow
			for (ISSABasicBlock b : suc) {
				if (b.getNumber() > loop.getEndBasicBlockNumber()) { // find block that skip out
					// trace back to the conditional branch until the begin_block
					Set<Integer> CBB = traceBackCBB(cfg, bb.getNumber(), loop.getBeginBasicBlockNumber());
					loop.conditional_branch_block.addAll(CBB);
				}
			}
		}
	}

	public Set<Integer> traceBackCBB(SSACFG cfg, int outBB, int beginBB) {
		BasicBlock bb = cfg.getBasicBlock(outBB);
		Set<Integer> result = new HashSet<Integer>();

		if (cfg.getNormalSuccessors(bb).size() > 1)// the current node is branch to skip out
			result.add(outBB);

		if (outBB <= beginBB)
			return result;

		Collection<ISSABasicBlock> bbs = cfg.getNormalPredecessors(bb);
		for (ISSABasicBlock pb : bbs) {
			if (cfg.getNormalSuccessors(pb).size() > 1)// the current node is branch to skip out
				result.add(pb.getNumber());
			result.addAll(traceBackCBB(cfg, pb.getNumber(), beginBB));
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
		 * while (true) { System.out.println("while - true..."); if(a[0] > 0) { a[0] ++;
		 * break; } }
		 */

	}

	public void statementAnalysis(Statement s, LoopInfo loop) {
		if (s.toString().indexOf("< Primordial,") >= 0)
			return;
//		System.out.println("#" + s);

		switch (s.getKind()) {

		case NORMAL:
			parseNormalStatement((NormalStatement) s, loop);
			return;
		case PHI:
			parsePHIStatement((PhiStatement) s, loop);
			return;
		case NORMAL_RET_CALLER:
			parseNormalCaller((NormalReturnCaller) s, loop);
			return;
		case EXC_RET_CALLER:
			parseExcCaller((ExceptionalReturnCaller) s, loop);
			return;
		case PARAM_CALLEE:
			parseParamCallee((ParamCallee) s, loop);
			return;
		case PARAM_CALLER:// ZC - unnecessary - may introduce unrelated variable
//			parseParamCaller((ParamCaller) s, loop);
			return;
		case HEAP_PARAM_CALLEE: // no heap info in thin-slice
		case HEAP_PARAM_CALLER:
		case HEAP_RET_CALLEE:
		case HEAP_RET_CALLER:
			HeapStatement h = (HeapStatement) s;
			System.out.println("#HeapStatement: " + h.getKind() + "\\n" + h.getNode() + "\\n" + h.getLocation());
			return;
		case NORMAL_RET_CALLEE:
			return;
		case EXC_RET_CALLEE:
			return;
		case CATCH://TODO maybe not a business
			return;
		case PI:
		default:
			break;
		}
		System.err.println("! Unanalysis statement - " + s.toString());

	}

	public void parseNormalStatement(NormalStatement s, LoopInfo loop) {
		SSAInstruction inst = s.getInstruction();

		// 1st-level instruction
		if (inst instanceof SSAConditionalBranchInstruction) {
			// System.err.println("SSAConditionalBranchInstruction");
			SSAConditionalBranchInstruction ssa = (SSAConditionalBranchInstruction) inst;
			for (int i = 0; i < ssa.getNumberOfDefs(); i++) {
				Pair<CGNode, Integer> e = new Pair<CGNode, Integer>(s.getNode(), (Integer) ssa.getDef(i));
				loop.V_def.add(e);
				loop.V_nul.remove(e);
			}

			for (int i = 0; i < ssa.getNumberOfUses(); i++) {
				Pair<CGNode, Integer> e = new Pair<CGNode, Integer>(s.getNode(), (Integer) ssa.getUse(i));
				if (!loop.V_def.contains(e)) // if this variable have not been def
					loop.V_nul.add(e);
			}
			return;
		}

		// 2nd-level instruction
		else if (inst instanceof SSAArrayLengthInstruction) { // we regard field as a special variable
			// System.err.println("SSAArrayLengthInstruction");
			SSAArrayLengthInstruction ssa = (SSAArrayLengthInstruction) inst;
			for (int i = 0; i < ssa.getNumberOfDefs(); i++) {
				Pair<CGNode, Integer> e = new Pair<CGNode, Integer>(s.getNode(), (Integer) ssa.getDef(i));
				loop.V_def.add(e);
				loop.V_nul.remove(e);
			}

			for (int i = 0; i < ssa.getNumberOfUses(); i++) {
				Pair<CGNode, Integer> e = new Pair<CGNode, Integer>(s.getNode(), (Integer) ssa.getUse(i));
				if (!loop.V_def.contains(e)) // if this variable have not been def
					loop.V_nul.add(e);
			}
			return;
		} else if (inst instanceof SSABinaryOpInstruction) {
			// System.err.println("SSABinaryOpInstruction");
			SSABinaryOpInstruction ssa = (SSABinaryOpInstruction) inst;
			for (int i = 0; i < ssa.getNumberOfDefs(); i++) {
				Pair<CGNode, Integer> e = new Pair<CGNode, Integer>(s.getNode(), (Integer) ssa.getDef(i));
				loop.V_def.add(e);
				loop.V_nul.remove(e);
			}

			for (int i = 0; i < ssa.getNumberOfUses(); i++) {
				Pair<CGNode, Integer> e = new Pair<CGNode, Integer>(s.getNode(), (Integer) ssa.getUse(i));
				if (!loop.V_def.contains(e)) // if this variable have not been def
					loop.V_nul.add(e);
			}
			return;
		}
		// others
		else if (inst instanceof SSAFieldAccessInstruction) {
			if (inst instanceof SSAGetInstruction) {
//				System.err.println("SSAFieldAccessInstruction - SSAGetInstruction");
				SSAGetInstruction ssa = (SSAGetInstruction) inst;
				if (ssa.getDeclaredFieldType().getName().toString().matches("Ljava/util/[a-zA-Z]*Set")
						|| ssa.getDeclaredFieldType().getName().toString().matches("Ljava/util/[a-zA-Z]*Map")
						|| ssa.getDeclaredFieldType().getName().toString().matches("Ljava/util/[a-zA-Z]*List")
						|| ssa.getDeclaredFieldType().getName().toString().matches("Ljava/util/[a-zA-Z]*Collection")
						|| ssa.getDeclaredFieldType().getName().toString().matches("L.*Map")
						|| ssa.getDeclaredFieldType().getName().toString().matches("L.*map")) {
					loop.fieldCollection.add(new Pair<CGNode, SSAInstruction>(s.getNode(), ssa));
				}

				for (int i = 0; i < ssa.getNumberOfDefs(); i++) {
					Pair<CGNode, Integer> e = new Pair<CGNode, Integer>(s.getNode(), (Integer) ssa.getDef(i));
					loop.V_def.add(e);
					loop.V_nul.remove(e);
				}
			} else if (inst instanceof SSAPutInstruction) {
				// System.err.println("SSAFieldAccessInstruction - SSAPutInstruction");
				SSAPutInstruction ssa = (SSAPutInstruction) inst;
				if (ssa.getDeclaredFieldType().getName().toString().matches("Ljava/util/[a-zA-Z]*Set")
						|| ssa.getDeclaredFieldType().getName().toString().matches("Ljava/util/[a-zA-Z]*Map")
						|| ssa.getDeclaredFieldType().getName().toString().matches("Ljava/util/[a-zA-Z]*List")
						|| ssa.getDeclaredFieldType().getName().toString().matches("Ljava/util/[a-zA-Z]*Collection")
						|| ssa.getDeclaredFieldType().getName().toString().matches("L.*Map")
						|| ssa.getDeclaredFieldType().getName().toString().matches("L.*map")) {
					loop.fieldCollection.add(new Pair<CGNode, SSAInstruction>(s.getNode(), ssa));
				}
				for (int i = 0; i < ssa.getNumberOfDefs(); i++) {
					Pair<CGNode, Integer> e = new Pair<CGNode, Integer>(s.getNode(), (Integer) ssa.getDef(i));
					loop.V_def.add(e);
					loop.V_nul.remove(e);
				}
			} else {
				System.err.println("SSAFieldAccessInstruction - not exist??");
			}
			return;
		} else if (inst instanceof SSANewInstruction) {
			// System.err.println("SSANewInstruction");
			SSANewInstruction ssa = (SSANewInstruction) inst;
			for (int i = 0; i < ssa.getNumberOfDefs(); i++) {
				Pair<CGNode, Integer> e = new Pair<CGNode, Integer>(s.getNode(), (Integer) ssa.getDef(i));
				loop.V_def.add(e);
				loop.V_nul.remove(e);
			}
			return;
		} else if (inst instanceof SSAArrayReferenceInstruction) {
			// System.err.println("SSAArrayReferenceInstruction");
			SSAArrayReferenceInstruction ssa = (SSAArrayReferenceInstruction) inst;
			for (int i = 0; i < ssa.getNumberOfDefs(); i++) {
				Pair<CGNode, Integer> e = new Pair<CGNode, Integer>(s.getNode(), (Integer) ssa.getDef(i));
				loop.V_def.add(e);
				loop.V_nul.remove(e);
			}
			return;
		} else if (inst instanceof SSACheckCastInstruction) {
			// System.err.println("SSACheckCastInstruction");
			SSACheckCastInstruction ssa = (SSACheckCastInstruction) inst;
			for (int i = 0; i < ssa.getNumberOfDefs(); i++) {
				Pair<CGNode, Integer> e = new Pair<CGNode, Integer>(s.getNode(), (Integer) ssa.getDef(i));
				loop.V_def.add(e);
				loop.V_nul.remove(e);
			}

			for (int i = 0; i < ssa.getNumberOfUses(); i++) {
				Pair<CGNode, Integer> e = new Pair<CGNode, Integer>(s.getNode(), (Integer) ssa.getUse(i));
				if (!loop.V_def.contains(e)) // if this variable have not been def
					loop.V_nul.add(e);
			}
			return;
		} else if (inst instanceof SSALoadMetadataInstruction) {
			// System.err.println("SSALoadMetadataInstruction");
			SSALoadMetadataInstruction ssa = (SSALoadMetadataInstruction) inst;
			for (int i = 0; i < ssa.getNumberOfDefs(); i++) {
				Pair<CGNode, Integer> e = new Pair<CGNode, Integer>(s.getNode(), (Integer) ssa.getDef(i));
				loop.V_def.add(e);
				loop.V_nul.remove(e);
			}

			for (int i = 0; i < ssa.getNumberOfUses(); i++) {
				Pair<CGNode, Integer> e = new Pair<CGNode, Integer>(s.getNode(), (Integer) ssa.getUse(i));
				if (!loop.V_def.contains(e)) // if this variable have not been def
					loop.V_nul.add(e);
			}
			return;
		} else if (inst instanceof SSAConversionInstruction) {
			// System.err.println("SSAConversionInstruction");
			SSAConversionInstruction ssa = (SSAConversionInstruction) inst;
			for (int i = 0; i < ssa.getNumberOfDefs(); i++) {
				Pair<CGNode, Integer> e = new Pair<CGNode, Integer>(s.getNode(), (Integer) ssa.getDef(i));
				loop.V_def.add(e);
				loop.V_nul.remove(e);
			}

			for (int i = 0; i < ssa.getNumberOfUses(); i++) {
				Pair<CGNode, Integer> e = new Pair<CGNode, Integer>(s.getNode(), (Integer) ssa.getUse(i));
				if (!loop.V_def.contains(e)) // if this variable have not been def
					loop.V_nul.add(e);
			}
			return;
		} else if (inst instanceof SSAAbstractUnaryInstruction) {
			//System.err.println("SSAAbstractUnaryInstruction");
			SSAAbstractUnaryInstruction ssa = (SSAAbstractUnaryInstruction) inst;
			for (int i = 0; i < ssa.getNumberOfDefs(); i++) {
				Pair<CGNode, Integer> e = new Pair<CGNode, Integer>(s.getNode(), (Integer) ssa.getDef(i));
				loop.V_def.add(e);
				loop.V_nul.remove(e);
			}

			for (int i = 0; i < ssa.getNumberOfUses(); i++) {
				Pair<CGNode, Integer> e = new Pair<CGNode, Integer>(s.getNode(), (Integer) ssa.getUse(i));
				if (!loop.V_def.contains(e)) // if this variable have not been def
					loop.V_nul.add(e);
			}
			return;
		}

		// useless currently
		else if (inst instanceof SSAMonitorInstruction) {
			// System.err.println("SSAMonitorInstruction");
			return;
		} else if (inst instanceof SSAReturnInstruction) {
			// System.err.println("SSAReturnInstruction");
			return;
		} else if (inst instanceof SSAAbstractThrowInstruction) {
			//System.err.println("SSAAbstractThrowInstruction");
			return;
		} 

		// TODO uncomplished or unkown inst
		else if (inst instanceof ReflectiveMemberAccess) {
			System.err.println("ReflectiveMemberAccess");
		} else if (inst instanceof SSAAbstractInvokeInstruction) {
			if (inst instanceof SSAInvokeInstruction) {
				System.err.println("SSAAbstractInvokeInstruction - SSAInvokeInstruction");
			} else {
				System.err.println("SSAAbstractInvokeInstruction - others");
			}
		} else if (inst instanceof SSAAddressOfInstruction) {
			System.err.println("SSAAddressOfInstruction");
		} else if (inst instanceof SSAComparisonInstruction) {
			System.err.println("SSAComparisonInstruction");
			return;
		} else if (inst instanceof SSAGetCaughtExceptionInstruction) {
			System.err.println("SSAGetCaughtExceptionInstruction");
		} else if (inst instanceof SSAGotoInstruction) {
			System.err.println("SSAGotoInstruction");
		} else if (inst instanceof SSAInstanceofInstruction) {
			System.err.println("SSAInstanceofInstruction");
		} else if (inst instanceof SSAPhiInstruction) {
			System.err.println("SSAPhiInstruction");
		} else if (inst instanceof SSAStoreIndirectInstruction) {
			System.err.println("SSAStoreIndirectInstruction");
		} else if (inst instanceof SSASwitchInstruction) {
			System.err.println("SSASwitchInstruction");
		} else {
			System.err.println("SSAInstruction unkown type");
		}
		System.err.println(inst);
	}

	public void parsePHIStatement(PhiStatement s, LoopInfo loop) {
		SSAPhiInstruction ssa = s.getPhi();
		for (int i = 0; i < ssa.getNumberOfDefs(); i++) {
			Pair<CGNode, Integer> e = new Pair<CGNode, Integer>(s.getNode(), (Integer) ssa.getDef(i));
			loop.V_def.add(e);
			loop.V_nul.remove(e);
		}

		for (int i = 0; i < ssa.getNumberOfUses(); i++) {
			Pair<CGNode, Integer> e = new Pair<CGNode, Integer>(s.getNode(), (Integer) ssa.getUse(i));
			if (!loop.V_def.contains(e)) // if this variable have not been def
				loop.V_nul.add(e);
		}
	}

	public void parseNormalCaller(NormalReturnCaller s, LoopInfo loop) {
		SSAAbstractInvokeInstruction ssa = s.getInstruction();
		if (iolooputil.isTimeConsumingSSA(ssa) || iolooputil.isJavaIOSSA(ssa)) {
			loop.IOs.add(s);
		}
		for (int i = 0; i < ssa.getNumberOfDefs(); i++) {
			Pair<CGNode, Integer> e = new Pair<CGNode, Integer>(s.getNode(), (Integer) ssa.getDef(i));
			loop.V_def.add(e);
			loop.V_nul.remove(e);
		}
		for (int i = 0; i < ssa.getNumberOfUses(); i++) {
			Pair<CGNode, Integer> e = new Pair<CGNode, Integer>(s.getNode(), (Integer) ssa.getUse(i));
			if (!loop.V_def.contains(e)) // if this variable have not been def
				loop.V_nul.add(e);
		}
	}

	public void parseExcCaller(ExceptionalReturnCaller s, LoopInfo loop) {
		SSAAbstractInvokeInstruction ssa = s.getInstruction();
		if (iolooputil.isTimeConsumingSSA(ssa) || iolooputil.isJavaIOSSA(ssa)) {
			loop.IOs.add(s);
		}
		for (int i = 0; i < ssa.getNumberOfDefs(); i++) {
			Pair<CGNode, Integer> e = new Pair<CGNode, Integer>(s.getNode(), (Integer) ssa.getDef(i));
			loop.V_def.add(e);
			loop.V_nul.remove(e);
		}

		Pair<CGNode, Integer> e = new Pair<CGNode, Integer>(s.getNode(), s.getValueNumber());
		if (!loop.V_def.contains(e)) // if this variable have not been def
			loop.V_nul.add(e);
	}

	public void parseParamCallee(ParamCallee s, LoopInfo loop) {
		Pair<CGNode, Integer> e = new Pair<CGNode, Integer>(s.getNode(), s.getValueNumber());
		loop.V_def.add(e);
		loop.V_nul.remove(e);
	}

	public void parseParamCaller(ParamCaller s, LoopInfo loop) { // ZC - unnecessary
		// can be ignored, walaslicer will auto add related instruction, this process
		// may introduce unnecessary variable
		SSAAbstractInvokeInstruction ssa = s.getInstruction();
		if (iolooputil.isTimeConsumingSSA(ssa) || iolooputil.isJavaIOSSA(ssa)) {
			loop.IOs.add(s);
		}
		for (int i = 0; i < ssa.getNumberOfDefs(); i++) {
			Pair<CGNode, Integer> e = new Pair<CGNode, Integer>(s.getNode(), (Integer) ssa.getDef(i));
			loop.V_def.add(e);
			loop.V_nul.remove(e);
		}
		for (int i = 0; i < ssa.getNumberOfUses(); i++) {
			Pair<CGNode, Integer> e = new Pair<CGNode, Integer>(s.getNode(), (Integer) ssa.getUse(i));
			if (!loop.V_def.contains(e)) // if this variable have not been def
				loop.V_nul.add(e);
		}
	}

	public CGNode findMethod(CallGraph cg, String Name, String methodCLass) {
		if (Name.equals(null) && methodCLass.equals(null))
			return null;
		Atom name = Atom.findOrCreateUnicodeAtom(Name);
		for (Iterator<? extends CGNode> it = cg.iterator(); it.hasNext();) {
			CGNode n = it.next();
			// System.err.println(n.getMethod().getName()+ " - "
			// +n.getMethod().getDeclaringClass().getName().toString());
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
			if (s.toString().indexOf("< Primordial,") >= 0)
				continue;
			System.out.println(s);
		}
	}

	public Collection<Statement> look4Value(Collection<Statement> slice, CGNode cgn, int valN) {
		// look for statements that contain (cgn,valN), as the
		Collection<Statement> result = new HashSet<Statement>();
		for (Statement s : slice) {
			if (!s.getNode().equals(cgn))
				continue;
			SSAInstruction ssa = null;
			switch (s.getKind()) {
			case NORMAL:
				ssa = ((NormalStatement) s).getInstruction();
				break;
			case PHI:
				ssa = ((PhiStatement) s).getPhi();
				break;
			case NORMAL_RET_CALLER:
				ssa = ((NormalReturnCaller) s).getInstruction();
				break;
			case EXC_RET_CALLER:
				ssa = ((ExceptionalReturnCaller) s).getInstruction();
				break;
			case PARAM_CALLEE:// ZC - unnecessary - no use value
			case PARAM_CALLER:// ZC - unnecessary - the same to analysis
			case HEAP_PARAM_CALLEE:
			case HEAP_PARAM_CALLER:
			case HEAP_RET_CALLEE:
			case HEAP_RET_CALLER:
			case EXC_RET_CALLEE:
			case CATCH:
			case PI:
			case NORMAL_RET_CALLEE:
			default:
				break;
			}
			if (ssa != null) {
				for (int i = 0; i < ssa.getNumberOfUses(); i++) {
					if (ssa.getUse(i) == valN)
						result.add(s);
				}
			}
		}
		return result;
	}
}
