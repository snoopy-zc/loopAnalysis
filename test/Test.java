import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class Test implements Runnable{
//public class Test extends Thread{
	private static final String ArrayList = null;
	Set<Integer> mySet;
	boolean myF;;
	int[] a = new int[10];
	
	public Test(Set<Integer> mySet,boolean x) {
		// TODO Auto-generated constructor stub
		this.mySet = mySet;
		this.myF = x;
	}
	
	public void testFor1(int c, int b){
		this.mySet.add(5);
		for(Integer x : mySet){
			System.out.println("hi1");
			if(x == 3)
			  break;
		}
		a[2] = 3;
		for(int i =  0; c < b; i++){
			System.out.println("hi2");
		}
		for(int i = 0; c - b < 0; i++){
			System.out.println("hi3");
		}
		if(a[2]>3) {
			System.out.println("a[2]>3");			
		}else {
			System.out.println("a[2]<=3");
		}
	}
	
	/*public void testFor2(){
		int[] c = new int[10];
		c[2] = 3;
		for(int z = 0; z < 10; z++)
			this.mySet.add(3);
	
		while(this.myF){
			this.mySet.add(99);
		}

		while(true){
			this.mySet.add(4);
		}
	}
	
	public void testFor3(){
		testFor1();
	}
	
	public void testFor4(){
		Set<Integer> localSet = new HashSet<Integer>();
		localSet.add(3);
		for(int z = 0; z < localSet.size(); z++){
			System.out.println("hi2");
		}
		int m;
		int y;
		for(m = 10; m < 100; m++){
			localSet.add(4);
		}
		
	}

	public void testFor5(){
		for(int z = 0; z < this.mySet.size(); z++){
			System.out.println("hi3");
		}	
	}

	public void testFor6(){
		Set<Integer> localSet = new HashSet<Integer>();
		localSet.add(3);
		for(Integer z : localSet){
			System.out.println("hi4");
		}

		for(int m = 10; m < 100; m++){
			localSet.add(4);
		}
	}

	public void testFor7(Set<Integer> z){
		for(int m = 0; m < z.size(); m++){
			System.out.println("hi5");
		}
		testFor3();
		Integer q = 100;
		z.add(q);
		Set<Integer> x = z;
		x.add(100);	
	}

	public void test8(){
	   int[] a = new int[10];
	   int[] b = a;
	}
	public void testFor8(){
		int[] q = new int[10];
		q[2] = 3;
		for(int m = 0; m < q[2]; m++){
			System.out.println("hi6");
		}
	
	}

	public void testFor9(){
		for(int m = 0; m < this.a[2]; m++ ){
			System.out.println("hi7");
		}
	}
	
	public void testIntergetLoop(){
		for(int i = 0; i < 10; i++){
			System.out.println("hi 81");
		}
		
		int x = 0;
		while(x<10){
			x++;
		}
		
		int y = 0;
		while(true){
			if(y>=10)
				break;
			y++;
		}
	}
	
	public void testCollection(){
		Set<Integer> set1 = new HashSet<Integer>();
		set1.add(10);
		for(Integer x : set1){
			System.out.println(x.intValue());
		}
		
		Iterator<Integer> itr = set1.iterator();
		while(itr.hasNext()){
			System.out.println(itr.next());
		}
		
		Iterator<Integer> it = set1.iterator();
		while(true){
			System.out.println(":");
			if(it.hasNext()){
				System.out.println("xxxx");
				break;
			}
			System.out.println(it.next());
		}
		
		//Iterator<Integer> iit = set1.iterator();
		//while(iit != null){
		//		iit = iit.hasNext() ? iit.next() : null;
		//}
	}
	
	public void testArray(){
		int[] x = new int[10];
		for(int i = 0; i < x.length; i++){
			System.out.println(x[i]);
		}
		
		for(int z : x){
			System.out.println(z);
		}
	}
	
	public static int testsss(){
		return 1;
	}*/

	public void run(){
		for(int i = 0; i < 10; i++)
			//System.out.println("hello....sb");
			this.mySet.add(5);

	}

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		
		Set<Integer> z = new HashSet<Integer>();
		//for(int m = 0 ; m < 2; m++){
			Test a = new Test(z,true); // for extends
			//a.start();

			(new Thread(a)).start(); // for implements
		//	a.testFor1();
		//}
		//for(int m = 0; m < 2; m++){
		//	a.testFor2();
		//}
		//a.testFor3();
		//a.testFor1();
		//z.add(4);
		//a.testFor7(z);
		/*int x;
		int y;
		int z;
		x = 1;
		System.out.println(x);
		x = 2;
		z = testsss();
		System.out.println(x);
		x++;
		x--;
		y = x + z;
		y = y+1;
		System.out.println(y);
		if(testsss() > 2 && y > 2 && x >3)
			System.out.println("x");
		if(testsss() > 2 || y > 2 || x >3)
			System.out.println("y");
		if(testsss() >2  && y > 2 || x >3)
			System.out.println("z");
		HashSet<String> mySet = new HashSet<String>(3);
		//mySet.add("a");
		//mySet.add("b");
		//mySet.add("c");
		//mySet.add("d");
		//System.out.println(mySet.size());
		//for(String x : mySet){
		//	System.out.println(x);
		//}*/
	}

}
