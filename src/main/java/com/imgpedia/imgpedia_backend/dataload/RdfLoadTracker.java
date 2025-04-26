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
 * Clase que rastrea qué archivos RDF ya han sido cargados para evitar recargas innecesarias.
 */
public class RdfLoadTracker {
    private final String trackerFilePath;
    private final Set<String> loadedFiles;
    private final Properties properties;
    
    /**
     * Constructor que inicializa el rastreador con un archivo específico.
     * 
     * @param trackerFilePath Ruta al archivo que almacena información de rastreo
     */
    public RdfLoadTracker(String trackerFilePath) {
        this.trackerFilePath = trackerFilePath;
        this.properties = new Properties();
        this.loadedFiles = new HashSet<>();
        
        loadTrackedFiles();
    }
    
    /**
     * Verifica si un archivo ya ha sido cargado previamente.
     * 
     * @param file El archivo a verificar
     * @return true si el archivo ya ha sido cargado, false en caso contrario
     */
    public boolean isFileLoaded(File file) {
        if (file == null || !file.exists()) {
            return false;
        }
        
        String fileKey = getFileKey(file);
        return loadedFiles.contains(fileKey);
    }
    
    /**
     * Marca un archivo como cargado correctamente.
     * 
     * @param file El archivo que se ha cargado correctamente
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
     * Elimina un archivo de la lista de archivos cargados.
     * Útil si se necesita volver a cargar un archivo específico.
     * 
     * @param file El archivo a eliminar del rastreo
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
     * Obtiene la lista de archivos que ya han sido cargados.
     * 
     * @return Un conjunto con las claves de los archivos cargados
     */
    public Set<String> getLoadedFiles() {
        return new HashSet<>(loadedFiles);
    }
    
    /**
     * Genera una clave única para un archivo basada en su ruta y última modificación.
     * 
     * @param file El archivo para el que generar la clave
     * @return Una clave única que representa el archivo
     */
    private String getFileKey(File file) {
        try {
            String filePath = file.getAbsolutePath();
            long lastModified = file.lastModified();
            long fileSize = file.length();
            
            // Crear una clave única basada en la ruta y metadatos del archivo
            String rawKey = filePath + "_" + lastModified + "_" + fileSize;
            
            // Opcional: crear un hash más compacto de la clave
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
            // En caso de error, usar solo la ruta del archivo
            return file.getAbsolutePath();
        }
    }
    
    /**
     * Carga la información de archivos previamente procesados.
     */
    private void loadTrackedFiles() {
        File trackerFile = new File(trackerFilePath);
        
        // Asegurarse de que el directorio exista
        Path directory = Paths.get(trackerFile.getParent());
        try {
            if (!Files.exists(directory)) {
                Files.createDirectories(directory);
            }
        } catch (IOException e) {
            System.err.println("Error creating directory for tracker file: " + e.getMessage());
        }
        
        // Cargar propiedades si el archivo existe
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
     * Guarda la información de archivos procesados.
     */
    private void saveTrackedFiles() {
        try (FileOutputStream fos = new FileOutputStream(trackerFilePath)) {
            properties.store(fos, "RDF Files Load Tracker - Last updated: " + new java.util.Date());
        } catch (IOException e) {
            System.err.println("Error saving tracker file: " + e.getMessage());
        }
    }
}