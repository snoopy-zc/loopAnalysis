package sa.loopsize;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;

public class Statistic {
	//final static String basePath = "/home/nemo/workspace/JXCascading-detector/src/sa/loopsize/mr-4813-log";
	//final static String basePath = "/home/nemo/workspace/JXCascading-detector/src/sa/loopsize/hdfs-5153-log";
	//final static String basePath = "/home/nemo/workspace/JXCascading-detector/src/sa/loopsize/hdfs-4584-log";
	//final static String basePath = "/home/nemo/workspace/JXCascading-detector/src/sa/loopsize/mr-2705-log";
	//final static String basePath = "/home/nemo/workspace/JXCascading-detector/src/sa/loopsize/mr-4576-log";
	//final static String basePath = "/home/nemo/workspace/JXCascading-detector/src/sa/loopsize/mr-4088-log";
	
	public static Path createFile(String basePath, String name) throws IOException{
		String abosultePath = basePath + "/" + name + ".txt";
		Path path = Paths.get(abosultePath);
		if(!Files.exists(path.getParent()))
			Files.createDirectories(path.getParent());
		if(!Files.exists(path)){
			return Files.createFile(path);
		}else{
			Files.delete(path);
			return Files.createFile(path);
		}
		
	}
	
	public static PrintStream writeToFileRawBegin(Path path){
		try {
			FileOutputStream fos = new FileOutputStream(path.toString());
			PrintStream ps = new PrintStream(fos);
			System.setOut(ps);
			return ps;
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
	
	public static void closeStream(PrintStream current){
		current.close();
	}
	
	public static void writeToFileRawEnd(PrintStream ps_console){
		System.setOut(ps_console);
		System.out.println("finish redirecting to System.out");
	}

}
