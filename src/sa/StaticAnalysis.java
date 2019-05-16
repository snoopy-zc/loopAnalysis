package sa;

import java.nio.file.Paths;

import com.system.Timer;

import sa.lockloop.LLAnalysis;
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
    	Timer timer = new Timer(jarsDir+".timer");
    	timer.tic("WalaAnalyzer begin");
		WalaAnalyzer walaAnalyzer = new WalaAnalyzer(jarsDir);
		timer.toc("WalaAnalyzer end");				
		timer.close();
	}
	
	
}
