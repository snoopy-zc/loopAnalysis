package sa;

import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;
import com.system.Timer;

import javafx.util.Pair;
import sa.lock.LockAnalyzer;
import sa.lockloop.CGNodeInfo;
import sa.lockloop.CGNodeList;
import sa.lockloop.LLAnalysis;
import sa.loop.BoundedLoopAnalyzer;
import sa.loop.LoopAnalyzer;
import sa.loop.LoopInfo;
import sa.loop.NestedLoopAnalyzer;
import sa.loop.TCLoopAnalyzer;
import sa.wala.WalaAnalyzer;

public class StaticAnalysis {

	// WalaAnalyzer walaAnalyzer;
	String projectDir;
	String jarsDir;

	public static void main(String[] args) {
		new StaticAnalysis(args);
	}

	/**
	 * @param args args[0] - projectDir, eg, ${workspace_loc}/loopAnalysis args[1] -
	 *             jarsDir, eg, ${workspace_loc}/loopAnalysis/src/sa/res/ha-4584
	 */
	public StaticAnalysis(String[] args) {
		projectDir = args[0];
		jarsDir = args[1];
		// doWork();
		scaleAnalysis();
	}

	public void doWork() {
		System.out.println("JX - INFO - StaticAnalysis.doWork");

		// Timer timer = new Timer( Paths.get(jarsDir, "wala-timer.txt") );
		Timer timer = new Timer(jarsDir + ".timer");
		timer.tic("WalaAnalyzer begin");
		WalaAnalyzer walaAnalyzer = new WalaAnalyzer(jarsDir);
		timer.toc("WalaAnalyzer end");

		LLAnalysis jxLocks = new LLAnalysis(walaAnalyzer, projectDir, timer);

		timer.close();
	}

	public void scaleAnalysis() {
		System.out.println("JX - INFO - StaticAnalysis.scaleAnalysis");

//		jarsDir = Paths.get(projectDir, "test").toString();
//		jarsDir = Paths.get(jarsDir, "sample1").toString();
		Timer timer = new Timer(jarsDir + ".timer");
		timer.tic("WalaAnalyzer begin");
		WalaAnalyzer walaAnalyzer = new WalaAnalyzer(jarsDir);
		timer.toc("WalaAnalyzer end");

		try {
			// walaAnalyzer.testIR();
			CGNodeList cgnl = new CGNodeList(walaAnalyzer.getCallGraph());
			LoopAnalyzer loopAnalyzer = new LoopAnalyzer(walaAnalyzer, cgnl);
			loopAnalyzer.doWork();
			timer.toc("loopAnalysis end");

			NestedLoopAnalyzer nestedLoopAnalyzer = new NestedLoopAnalyzer(walaAnalyzer, loopAnalyzer, cgnl);
			nestedLoopAnalyzer.doWork();
			timer.toc("nestedLoopAnalyzer end");
			
			TCLoopAnalyzer tcLoopAnalyzer = new TCLoopAnalyzer(walaAnalyzer, loopAnalyzer, cgnl);
			tcLoopAnalyzer.doWork();
			timer.toc("tcLoopAnalyzer end");
			
			//hb3483
			//mr4576
			//ha4584
			//hd3990
			//hd4186
			//hd5153
			//mr4813
			
			for (CGNodeInfo cgn : loopAnalyzer.getLoopCGNodes()) {
				for (String bugid : mr4813.keySet()) {
					if (check_each(cgn, bugid)) {
						for(LoopInfo lp : cgn.getLoops())
							if(lp.numOfTcOperations_recusively>0) //pruning
								scaleLoops.get(bugid).add(lp);
					}
				}
			}

			timer.toc("Pruning end");			
			
			for (String bugid : scaleLoops.keySet()) {
				System.out.println("\n\n\n-------------zc - bug ID: " + bugid + "-----------------"
						+ scaleLoops.get(bugid).size());
				BoundedLoopAnalyzer boundedLoopAnalyzer = new BoundedLoopAnalyzer(walaAnalyzer, scaleLoops.get(bugid),
						this.projectDir);
				boundedLoopAnalyzer.doWork();
			}
			timer.toc("boundedLoopAnalyzer end");

			timer.toc("\n\n All done!");

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		timer.close();
//		Set<Pair<WalaAnalyzer,Integer>> s = new HashSet<Pair<WalaAnalyzer,Integer>>();
//		s.add(new Pair<WalaAnalyzer,Integer>(walaAnalyzer,1));
//		s.add(new Pair<WalaAnalyzer,Integer>(walaAnalyzer,1));
//		System.out.println(s.size());
//		s.remove(new Pair<WalaAnalyzer,Integer>(walaAnalyzer,2));
//		System.out.println(s.size());

	}

	public boolean check_each(CGNodeInfo a, String bugID) {
		return a.getCGNode().getMethod().toString().indexOf(scaleCheckList.get(bugID).toString()) >= 0;
	}

	HashMap<String, String> scaleCheckList = new HashMap<String, String>() {
		{
			put("mr4576-1", "FileUtil, copy");
			put("mr4576-2", "IOUtils, copyBytes");
			put("mr4813-1", "FileOutputCommitter, commitJob");
			put("mr4813-2", "FileOutputCommitter, mergePaths");

			put("hb3483-1", "MemStoreFlusher, flushSomeRegions");
			put("hb3483-2.1", "HRegion, internalFlushcache");
			put("hb3483-2.2", "CompactSplitThread, internalFlushcache");
			put("hb3483-3.1", "Store, internalFlushCache");
			put("hb3483-3.2", "Store, updateStorefiles");

			put("hd2379-1", "FSVolumeSet, getBlockInfo");
			put("hd2379-2", "FSDir, getBlockInfo");
			put("hd2379-3", "File, listFiles");
			put("hd2379-4", "FSDataset, getGenerationStampFromFile");

			put("hd3990-1", "DatanodeManager, getDatanodeListForReport");
			put("hd3990-2", "DatanodeManager, checkInList");

			put("hd4186-1", "LeaseManager, checkLeases");
			put("hd4186-2", "FSEditlog, logSync");

			put("hd5153-1", "NameNodeRpcServer, blockReport");
			put("hd5153-2", "BlockManager, processFirstBlockReport");
			put("hd5153-3", "invalidateCorruptReplicas");
			put("hd5153-4", "reportDiff");
			
			put("mr4813-1", "FileOutputCommitter, commitJob");
			put("mr4813-2", "FileOutputCommitter, mergePaths");
		}
	};

	HashMap<String, String> hb3483 = new HashMap<String, String>() {
		{
			put("hb3483-1", "MemStoreFlusher, flushSomeRegions");
			put("hb3483-2.1", "HRegion, internalFlushcache");
			put("hb3483-2.2", "CompactSplitThread, internalFlushcache");
			put("hb3483-3.1", "Store, internalFlushCache");
			put("hb3483-3.2", "Store, updateStorefiles");
		}
	};

	HashMap<String, String> mr4576 = new HashMap<String, String>() {
		{
			put("mr4576-1", "FileUtil, copy");
			put("mr4576-2", "IOUtils, copyBytes");
			put("mr4813-1", "FileOutputCommitter, commitJob");
			put("mr4813-2", "FileOutputCommitter, mergePaths");
		}
	};

	HashMap<String, String> ha4584 = new HashMap<String, String>() {
		{
			put("hd2379-1", "FSVolumeSet, getBlockInfo");
			put("hd2379-2", "FSDir, getBlockInfo");
			put("hd2379-3", "File, listFiles");
			put("hd2379-4", "FSDataset, getGenerationStampFromFile");
		}
	};

	HashMap<String, String> hd3990 = new HashMap<String, String>() {
		{
			put("hd3990-1", "DatanodeManager, getDatanodeListForReport");
			put("hd3990-2", "DatanodeManager, checkInList");
		}
	};

	HashMap<String, String> hd4186 = new HashMap<String, String>() {
		{
			put("hd4186-1", "LeaseManager, checkLeases");
			put("hd4186-2", "FSEditlog, logSync");
		}
	};
	HashMap<String, String> hd5153 = new HashMap<String, String>() {
		{
			put("hd5153-1", "NameNodeRpcServer, blockReport");
			put("hd5153-2", "BlockManager, processFirstBlockReport");
			put("hd5153-3", "invalidateCorruptReplicas");
			put("hd5153-4", "reportDiff");
		}
	};
	HashMap<String, String> mr4813 = new HashMap<String, String>() {
		{
			put("mr4813-1", "FileOutputCommitter, commitJob");
			put("mr4813-2", "FileOutputCommitter, mergePaths");
		}
	};

	public HashMap<String, Collection<LoopInfo>> scaleLoops = new HashMap<String, Collection<LoopInfo>>() {
		// for analysis loop bound - Class BoundedLoopAnalyzer.java
		{
			put("mr4576-1", new HashSet<LoopInfo>());
			put("mr4576-2", new HashSet<LoopInfo>());
			put("mr4813-1", new HashSet<LoopInfo>());
			put("mr4813-2", new HashSet<LoopInfo>());

			put("hb3483-1", new HashSet<LoopInfo>());
			put("hb3483-2.1", new HashSet<LoopInfo>());
			put("hb3483-2.2", new HashSet<LoopInfo>());
			put("hb3483-3.1", new HashSet<LoopInfo>());
			put("hb3483-3.2", new HashSet<LoopInfo>());

			put("hd2379-1", new HashSet<LoopInfo>());// same to ha4584
			put("hd2379-2", new HashSet<LoopInfo>());
			put("hd2379-3", new HashSet<LoopInfo>());
			put("hd2379-4", new HashSet<LoopInfo>());

			put("hd3990-1", new HashSet<LoopInfo>());
			put("hd3990-2", new HashSet<LoopInfo>());

			put("hd4186-1", new HashSet<LoopInfo>());
			put("hd4186-2", new HashSet<LoopInfo>());

			put("hd5153-1", new HashSet<LoopInfo>());
			put("hd5153-2", new HashSet<LoopInfo>());
			put("hd5153-3", new HashSet<LoopInfo>());
			put("hd5153-4", new HashSet<LoopInfo>());
			
			put("mr4813-1", new HashSet<LoopInfo>());
			put("mr4813-2", new HashSet<LoopInfo>());
		}
	};

}
