import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class Test implements Runnable {
//public class Test extends Thread{
	private static final String ArrayList = null;
	Set<Integer> mySet;
	boolean myF;
	int[] a;
	int[] aa;

	public Test(Set<Integer> mySet, boolean x) {
		this.mySet = mySet;
		this.myF = x;
		this.a = new int[10];// constant length
		this.aa = new int[mySet.size() + 5];// variable length
	}

	public void testFor1(int b, int c) {

		// for loop
		for (int i = 0; i < 5; i++) {
			System.out.println("for - normal for loop...");
		}

		for (int i = 0; i < b; i++) {
			System.out.println("for - inputed variable...");
		}

		for (int i = 0; i < a.length; i++) {
			System.out.println("for - constant array length...");
		}

		for (int i = 0; i < aa.length; i++) {
			System.out.println("for - variable array length...");
		}

		for (int i = 0; i < mySet.size(); i++) {
			System.out.println("for - collection size...");
		}

		for (Iterator iter = mySet.iterator(); iter.hasNext();) {
			Integer ele = (Integer)iter.next();
			System.out.println("for - iterator loop...");
		}

		// for each loop
		this.mySet.add(5);
		for (Integer x : mySet) {
			System.out.println("for each - regular collection loop...");
		}

		for (int x : a) {
			System.out.println("for each - regular array loop...?");
		}

		// while loop
		while (true) { // constant true
			//System.out.println("while - true...");
			if (a[0] > 0) {
				a[0] ++;
				break;
			}
		}
		while (true) { // constant true
			do {
				a[0]++;
				System.out.println("do while - a[0] > 0...");
			}while(a[0]>0);//conditonal branch -> last instruction
			break;
		}
		while (true) { // constant true
			System.out.println("while - true...");
			if (a[0] > 0)
				break;
			a[0]++;
		}

		while (myF) { // signal
			System.out.println("while - signal...");
		}

		while (b > c) { // update variable
			System.out.println("while - b > c...");
			c++;
		}

		while (b > c + 1) { // update variable
			System.out.println("while - b > c+1...");
			b++;
		}

		while (b + c + 1 > 0) { // update variable
			System.out.println("while - b+c+1 > 0...");
			b++;
		}
		
		do {
			a[0]++;
			System.out.println("do while - a[0] > 0...");
		}while(a[0]>0);//conditonal branch -> last instruction

		// others...
}

	public void test2() {

		this.myF = false;
	}

	public void run() {
		for (int i = 0; i < 10; i++)
			// System.out.println("hello....sb");
			this.mySet.add(5);

		int a = 3;
		int b = 9;
		int x = a + b;
		int y = b - a;
		int z = a - y;
		testFor1(x, z);

	}

	public static void main(String[] args) {

		Set<Integer> z = new HashSet<Integer>();
		Test a = new Test(z, true);
		(new Thread(a)).start(); // for implements

	}

}
