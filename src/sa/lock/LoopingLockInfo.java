package sa.lock;

import java.util.ArrayList;
import java.util.List;

import com.ibm.wala.ipa.callgraph.CGNode;

import javafx.util.Pair;
import sa.loop.LoopInfo;
import sa.tcop.PathInfo;



public class LoopingLockInfo {

	CGNode function;
	LockInfo lock;
	List<LoopInfo> inner_loops = null;           //inner loop in current level  //Type - ArrayList<LoopInfo>
  
	int max_depthOfLoops;
	List<Integer> function_chain_for_max_depthOfLoops;
	List<Integer> hasLoops_in_current_function_for_max_depthOfLoops;
	
	List<Pair<LoopInfo,PathInfo>> loop_paths = null;
	
	
	public List<LoopInfo> getLoops(){
		return inner_loops;
	}
	
	public int get_max_depthOfLoops(){
		return max_depthOfLoops;
	}
	
	public LockInfo getLock() {
		return lock;
	}
	
	public CGNode getCGNode() {
		return function;
	}
	
	public List<Pair<LoopInfo,PathInfo>> getLoopPaths(){
		return loop_paths;
	}
  
	public LoopingLockInfo() {
		this.function = null;
		this.lock = null;
		this.inner_loops = new ArrayList<LoopInfo>();
		this.loop_paths = new ArrayList<Pair<LoopInfo,PathInfo>>();
    
		this.max_depthOfLoops = 0;
		this.function_chain_for_max_depthOfLoops = new ArrayList<Integer>();
		this.hasLoops_in_current_function_for_max_depthOfLoops = new ArrayList<Integer>();
	}
	
}