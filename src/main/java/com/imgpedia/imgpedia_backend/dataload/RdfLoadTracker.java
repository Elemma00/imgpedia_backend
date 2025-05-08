package com.imgpedia.imgpedia_backend.dataload;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/**
 * Class that tracks which RDF files have already been loaded to avoid unnecessary reloads.
 */
public class RdfLoadTracker {
    private final String trackerFilePath;
    private final Set<String> loadedFiles;
    private final Properties properties;
    
    /**
     * Constructor that initializes the tracker with a specific file.
     * 
     * @param trackerFilePath Path to the file that stores tracking information
     */
    public RdfLoadTracker(String trackerFilePath) {
        this.trackerFilePath = trackerFilePath;
        this.properties = new Properties();
        this.loadedFiles = new HashSet<>();
        
        loadTrackedFiles();
    }
    
    /**
     * Checks if a file has already been loaded previously.
     * 
     * @param file The file to check
     * @return true if the file has already been loaded, false otherwise
     */
    public boolean isFileLoaded(File file) {
        if (file == null || !file.exists()) {
            return false;
        }
        
        String fileKey = getFileKey(file);
        return loadedFiles.contains(fileKey);
    }
    
    /**
     * Marks a file as successfully loaded.
     * 
     * @param file The file that has been successfully loaded
     */
    public void markFileAsLoaded(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        
        String fileKey = getFileKey(file);
        String timestamp = String.valueOf(System.currentTimeMillis());
        
        loadedFiles.add(fileKey);
        properties.setProperty(fileKey, timestamp);
        
        saveTrackedFiles();
    }
    
    /**
     * Removes a file from the list of loaded files.
     * Useful if a specific file needs to be reloaded.
     * 
     * @param file The file to remove from tracking
     */
    public void untrackFile(File file) {
        if (file == null) {
            return;
        }
        
        String fileKey = getFileKey(file);
        loadedFiles.remove(fileKey);
        properties.remove(fileKey);
        
        saveTrackedFiles();
    }
    
    /**
     * Gets the list of files that have already been loaded.
     * 
     * @return A set with the keys of loaded files
     */
    public Set<String> getLoadedFiles() {
        return new HashSet<>(loadedFiles);
    }
    
    /**
     * Generates a unique key for a file based on its path and last modification.
     * 
     * @param file The file for which to generate the key
     * @return A unique key representing the file
     */
    private String getFileKey(File file) {
        try {
            String filePath = file.getAbsolutePath();
            long lastModified = file.lastModified();
            long fileSize = file.length();
            
            // Create a unique key based on the file path and metadata
            String rawKey = filePath + "_" + lastModified + "_" + fileSize;
            
            // Optional: create a more compact hash of the key
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawKey.getBytes());
            StringBuilder hexString = new StringBuilder();
            
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            
            return filePath + "_" + hexString.substring(0, 8);
        } catch (Exception e) {
            // In case of error, use only the file path
            return file.getAbsolutePath();
        }
    }
    
    /**
     * Loads information about previously processed files.
     */
    private void loadTrackedFiles() {
        File trackerFile = new File(trackerFilePath);
        
        // Ensure the directory exists
        Path directory = Paths.get(trackerFile.getParent());
        try {
            if (!Files.exists(directory)) {
                Files.createDirectories(directory);
            }
        } catch (IOException e) {
            System.err.println("Error creating directory for tracker file: " + e.getMessage());
        }
        
        // Load properties if the file exists
        if (trackerFile.exists()) {
            try (FileInputStream fis = new FileInputStream(trackerFile)) {
                properties.load(fis);
                loadedFiles.addAll(properties.stringPropertyNames());
                System.out.println("Loaded " + loadedFiles.size() + " tracked files from " + trackerFilePath);
            } catch (IOException e) {
                System.err.println("Error loading tracker file: " + e.getMessage());
            }
        }
    }
    
    /**
     * Saves information about processed files.
     */
    private void saveTrackedFiles() {
        try (FileOutputStream fos = new FileOutputStream(trackerFilePath)) {
            properties.store(fos, "RDF Files Load Tracker - Last updated: " + new java.util.Date());
        } catch (IOException e) {
            System.err.println("Error saving tracker file: " + e.getMessage());
        }
    }
}