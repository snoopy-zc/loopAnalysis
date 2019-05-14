package sa.loopsize;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.propagation.HeapModel;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.callgraph.propagation.PointerKey;
import com.ibm.wala.util.intset.OrdinalSet;

public class AliasAnalysis {
	
	public static void getAA(PointerAnalysis pa, CGNode cgN, int x){
		HeapModel hm = pa.getHeapModel();
		System.out.println(hm.toString());
		PointerKey ptrKey = hm.getPointerKeyForLocal(cgN,x);
		System.out.println(ptrKey.toString());
		OrdinalSet<InstanceKey> ptsTo = pa.getPointsToSet(ptrKey);
		System.out.println(ptsTo.size());
		for(InstanceKey tmp : ptsTo){
			System.out.println(tmp);
		}
	}
}
