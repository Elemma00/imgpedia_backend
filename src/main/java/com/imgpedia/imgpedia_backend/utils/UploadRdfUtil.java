package com.imgpedia.imgpedia_backend.utils;

public class UploadRdfUtil {

    public static String getFileExtension(String filename) {
        if (filename == null) {
            return "";
        }
        
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return "";
        }
        
        return filename.substring(lastDotIndex).toLowerCase();
    }
    
    public static Boolean isValidRdfFile(String extension) {
        return extension.equals(".ttl") || 
               extension.equals(".rdf") || 
               extension.equals(".nt") ||
               extension.equals(".tar.gz");
    }
}
