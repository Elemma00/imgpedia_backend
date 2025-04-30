package com.imgpedia.imgpedia_backend.services;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.system.ErrorHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.imgpedia.imgpedia_backend.dataload.EncodeIRI;
import com.imgpedia.imgpedia_backend.logger.ImgpediaLogger;

import jakarta.annotation.PostConstruct;

@Service
public class RdfUploadService {

    @Autowired
    @Qualifier("rdfDataset")
    private Dataset dataset;

    @Autowired
    @Qualifier("rdfModel")
    private Model model;

    @Value("${upload.dir:#{systemProperties['java.io.tmpdir'] + '/imgpedia_uploads'}}")
    private String uploadDir;

    private final Map<String, Map<String, Object>> uploadStatus = new ConcurrentHashMap<>();

    public RdfUploadService() {}

    @PostConstruct
    public void init() {
        try {
            // Crear directorio de carga si no existe
            File uploadPath = new File(uploadDir);
            if (!uploadPath.exists()) {
                if (!uploadPath.mkdirs()) {
                    ImgpediaLogger.error("No se pudo crear el directorio de subida: " + uploadDir);
                } else {
                    ImgpediaLogger.info("Directorio de subida creado: " + uploadDir);
                    cleanUploadDirectory(uploadPath);
                }
            } else {
                ImgpediaLogger.info("Usando directorio de subida existente: " + uploadDir);
            }
        } catch (Exception e) {
            ImgpediaLogger.error("Error al inicializar el servicio de carga: " + e.getMessage());
        }
    }

    public boolean processUploadedFile(MultipartFile file, String uploadId, String targetFileName) {
        // Initialize upload status
        Map<String, Object> status = new HashMap<>();
        status.put("status", "processing");
        status.put("progress", 0);
        status.put("fileName", file.getOriginalFilename());
        uploadStatus.put(uploadId, status);
        File tempFile = null;

        try {
            tempFile = new File(uploadDir, targetFileName);
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                fos.write(file.getBytes());
            }
  
            boolean success;
            if (targetFileName.endsWith(".tar.gz")) {
                success = processCompressedFile(tempFile, uploadId);
            } else {
                success = processRdfFile(tempFile, uploadId);
            }
            
            // Update final status
            status.put("status", success ? "completed" : "failed");
            status.put("progress", 100);
            
            // Clean up temp file
            if (!tempFile.delete()) {
                ImgpediaLogger.warn("Could not delete temporary file: " + tempFile.getAbsolutePath());
            }
            
            return success;
        } catch (Exception e) {
            ImgpediaLogger.error("Error processing uploaded file: " + e.getMessage());
            status.put("status", "failed");
            status.put("error", e.getMessage());
            return false;
        } finally {
            if (tempFile != null && tempFile.exists()) {
                if (tempFile.delete()) {
                    ImgpediaLogger.info("Archivo temporal eliminado: " + tempFile.getName());
                } else {
                    ImgpediaLogger.warn("No se pudo eliminar archivo temporal: " + tempFile.getAbsolutePath());
                }
            }
        }
    }

    private boolean processRdfFile(File file, String uploadId) {
        try (InputStream inputStream = new FileInputStream(file)) {
            ImgpediaLogger.info("Loading RDF file: " + file.getAbsolutePath());
            
            dataset.begin(ReadWrite.WRITE);
            try {
                RDFParser.create()
                        .source(inputStream)
                        .lang(RDFLanguages.filenameToLang(file.getName()))
                        .errorHandler(createErrorHandler())
                        .parse(new EncodeIRI(model));
                
                dataset.commit();
                ImgpediaLogger.info("Successfully loaded RDF file: " + file.getName());
                return true;
            } catch (Exception e) {
                if (dataset.isInTransaction()) {
                    dataset.abort();
                }
                ImgpediaLogger.error("Error loading RDF file: " + e.getMessage());
                return false;
            } finally {
                if (dataset.isInTransaction()) {
                    dataset.end();
                }
            }
        } catch (Exception e) {
            ImgpediaLogger.error("Error reading file: " + e.getMessage());
            return false;
        }
    }

    private boolean processCompressedFile(File compressedFile, String uploadId) {
        ImgpediaLogger.info("Processing compressed file: " + compressedFile.getAbsolutePath());
        boolean overallSuccess = true;
        
        File tempDir = new File(uploadDir, "temp_" + uploadId);
        if (!tempDir.exists() && !tempDir.mkdirs()) {
            ImgpediaLogger.error("Failed to create temp directory: " + tempDir.getAbsolutePath());
            return false;
        }
        
        try (FileInputStream fileInputStream = new FileInputStream(compressedFile);
             GzipCompressorInputStream gzipInputStream = new GzipCompressorInputStream(fileInputStream);
             TarArchiveInputStream tarInput = new TarArchiveInputStream(gzipInputStream)) {
            
            TarArchiveEntry currentEntry;
            int totalEntries = 0;
            int processedEntries = 0;
            
            // First count the entries for progress tracking
            while ((currentEntry = tarInput.getNextTarEntry()) != null) {
                if (!currentEntry.isDirectory() && currentEntry.getName().endsWith(".ttl")) {
                    totalEntries++;
                }
            }
            
            // Reset the stream
            fileInputStream.getChannel().position(0);
            GzipCompressorInputStream newGzipStream = new GzipCompressorInputStream(fileInputStream);
            TarArchiveInputStream newTarInput = new TarArchiveInputStream(newGzipStream);
            
            // Now process the entries
            while ((currentEntry = newTarInput.getNextTarEntry()) != null) {
                if (currentEntry.isDirectory() || !currentEntry.getName().endsWith(".ttl")) {
                    continue;
                }
                
                String entryName = currentEntry.getName();
                String sanitizedName = entryName.replaceAll("[^a-zA-Z0-9.-]", "_");
                File tempFile = new File(tempDir, "tarentry_" + sanitizedName);
                
                try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    
                    while ((bytesRead = newTarInput.read(buffer)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                        if (bytesRead < buffer.length) {
                            break;
                        }
                    }
                }
                
                boolean entrySuccess = processTemporaryFile(tempFile, entryName);
                if (!entrySuccess) {
                    overallSuccess = false;
                }
                
                processedEntries++;
                
                // Update progress
                Map<String, Object> status = uploadStatus.get(uploadId);
                if (status != null) {
                    status.put("progress", (int)(((double)processedEntries / totalEntries) * 100));
                }
                
                if (!tempFile.delete()) {
                    ImgpediaLogger.warn("Failed to delete temp file: " + tempFile.getAbsolutePath());
                }
            }
            
        } catch (Exception e) {
            overallSuccess = false;
            ImgpediaLogger.error("Error processing compressed file: " + e.getMessage());
        } finally {
            deleteDirectory(tempDir);
        }
        
        return overallSuccess;
    }

        private boolean processTemporaryFile(File tempFile, String originalName) {
        dataset.begin(ReadWrite.WRITE);
        boolean success = false;
        
        try (InputStream inputStream = new FileInputStream(tempFile)) {
            ImgpediaLogger.info("Loading entry from temp file: " + originalName);
        
            Model tempModel = ModelFactory.createDefaultModel();
            
            RDFParser.create()
                    .source(inputStream)
                    .lang(RDFLanguages.TURTLE)
                    .errorHandler(createErrorHandler())
                    .parse(new EncodeIRI(tempModel));
                    
            int batchSize = 10000;
            int statementsAdded = 0;
            
            StmtIterator stmtIter = tempModel.listStatements();
            while (stmtIter.hasNext()) {
                for (int i = 0; i < batchSize && stmtIter.hasNext(); i++) {
                    model.add(stmtIter.next());
                    statementsAdded++;
                }
            }
            
            dataset.commit();
            success = true;
            ImgpediaLogger.info("Successfully loaded entry: " + originalName + " (" + statementsAdded + " statements)");
        } catch (Exception e) {
            ImgpediaLogger.error("Error loading entry: " + e.getMessage());
            if (dataset.isInTransaction()) {
                dataset.abort();
            }
        } finally {
            if (dataset.isInTransaction()) {
                dataset.end();
            }
        }
        
        return success;
    }
    
    private ErrorHandler createErrorHandler() {
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
    
    private void deleteDirectory(File directory) {
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

    private void cleanUploadDirectory(File directory) {
        if (directory.exists() && directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        if (file.delete()) {
                            ImgpediaLogger.info("Archivo residual eliminado: " + file.getAbsolutePath());
                        } else {
                            ImgpediaLogger.warn("No se pudo eliminar archivo residual: " + file.getAbsolutePath());
                        }
                    }
                }
            }
        }
    }
    
    public Map<String, Object> getUploadStatusById(String uploadId) {
        return uploadStatus.getOrDefault(uploadId, Map.of("status", "not_found"));
    }
}
