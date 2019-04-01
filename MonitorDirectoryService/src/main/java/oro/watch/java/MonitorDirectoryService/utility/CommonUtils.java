package oro.watch.java.MonitorDirectoryService.utility;

import java.util.List;

public class CommonUtils {
	
	public static final String relativePath(String filePath, String watchDir) {
    	if(filePath.length() == watchDir.length()) {
    		return "";
    	}
    	return filePath.substring(watchDir.length() + 1);
    }
	
	  public static  boolean checkFileType(String fileName,List<String> fileExtensions) {
	    	String extension = fileName.split("\\.")[1];
			return fileExtensions.stream().anyMatch(e -> e.trim().equalsIgnoreCase(extension));
		}

}
