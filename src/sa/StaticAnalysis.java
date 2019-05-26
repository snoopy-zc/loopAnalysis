package sa;

import java.nio.file.Paths;

import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;
import com.system.Timer;

import sa.lock.LockAnalyzer;
import sa.lockloop.CGNodeList;
import sa.lockloop.LLAnalysis;
import sa.loop.BoundedLoopAnalyzer;
import sa.loop.LoopAnalyzer;
import sa.wala.WalaAnalyzer;

public class StaticAnalysis {

	//WalaAnalyzer walaAnalyzer;
	String projectDir;
	String jarsDir;
	
	
	public static void main(String[] args) {
		new StaticAnalysis(args);
	}
	
	/**
	 * @param args
	 * 	args[0] - projectDir, eg, ${workspace_loc}/loopAnalysis
	 * 	args[1] - jarsDir, eg, ${workspace_loc}/loopAnalysis/src/sa/res/ha-4584
	 */
	public StaticAnalysis(String[] args) {
		projectDir = args[0];
		jarsDir = args[1];
		//doWork();
		test();
	}
	
	
	public void doWork() {
		System.out.println("JX - INFO - StaticAnalysis.doWork");		

    	//Timer timer = new Timer( Paths.get(jarsDir, "wala-timer.txt") );
    	Timer timer = new Timer(jarsDir+".timer");
    	timer.tic("WalaAnalyzer begin");
		WalaAnalyzer walaAnalyzer = new WalaAnalyzer(jarsDir);
		timer.toc("WalaAnalyzer end");
				
		LLAnalysis jxLocks = new LLAnalysis(walaAnalyzer, projectDir, timer);

		timer.close();
	}
	
	public void test() {
		System.out.println("JX - INFO - StaticAnalysis.test");		

		jarsDir = Paths.get(projectDir, "test").toString();
		//jarsDir = "${workspace_loc}/test"
    	Timer timer = new Timer(jarsDir+".timer");
    	timer.tic("WalaAnalyzer begin");
		WalaAnalyzer walaAnalyzer = new WalaAnalyzer(jarsDir);
    	timer.tic("WalaAnalyzer end");

		try {
			//walaAnalyzer.testIR();			
			CGNodeList cgnl = new CGNodeList(walaAnalyzer.getCallGraph());
			LoopAnalyzer loopAnalyzer = new LoopAnalyzer(walaAnalyzer, cgnl);
			loopAnalyzer.doWork();
	    	timer.tic("loopAnalysis end");		
			BoundedLoopAnalyzer boundedLoopAnalyzer = new BoundedLoopAnalyzer(walaAnalyzer, loopAnalyzer, cgnl, this.projectDir);
			boundedLoopAnalyzer.doWork();
	    	timer.tic("boundedLoopAnalyzer end");	
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}			
		timer.close();
		
	}
	
	
}
