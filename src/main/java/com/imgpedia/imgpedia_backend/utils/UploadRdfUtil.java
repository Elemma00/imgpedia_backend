package com.imgpedia.imgpedia_backend.utils;

import java.io.File;

import org.apache.jena.riot.system.ErrorHandler;

import com.imgpedia.imgpedia_backend.logger.ImgpediaLogger;

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


    public static ErrorHandler createErrorHandler() {
        return new ErrorHandler() {
            @Override
            public void warning(String message, long line, long col) {
             
            }

            @Override
            public void error(String message, long line, long col) {
               
            }

            @Override
            public void fatal(String message, long line, long col) {
               
            }
        };
    }
    
    public static void deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        if (!file.delete()) {
                            ImgpediaLogger.warn("Failed to delete file: " + file.getAbsolutePath());
                        }
                    }
                }
            }
            if (!directory.delete()) {
                ImgpediaLogger.warn("Failed to delete directory: " + directory.getAbsolutePath());
            }
        }
    }

    public static void cleanUploadDirectory(File directory) {
        if (directory.exists() && directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        if (file.delete()) {
                            ImgpediaLogger.info("Residual file removed: " + file.getAbsolutePath());
                        } else {
                            ImgpediaLogger.warn("An error occurred removing the residual file: " + file.getAbsolutePath());
                        }
                    }
                }
            }
        }
    }

    

}
