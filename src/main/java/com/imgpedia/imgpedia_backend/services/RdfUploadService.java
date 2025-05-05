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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.imgpedia.imgpedia_backend.dataload.EncodeIRI;
import com.imgpedia.imgpedia_backend.logger.ImgpediaLogger;
import com.imgpedia.imgpedia_backend.utils.MessagesLogs;
import static com.imgpedia.imgpedia_backend.utils.UploadRdfUtil.cleanUploadDirectory;
import static com.imgpedia.imgpedia_backend.utils.UploadRdfUtil.createErrorHandler;
import static com.imgpedia.imgpedia_backend.utils.UploadRdfUtil.deleteDirectory;

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
            File uploadPath = new File(uploadDir);
            if (!uploadPath.exists()) {
                if (!uploadPath.mkdirs()) {
                    ImgpediaLogger.error(MessagesLogs.UPLOAD_DIR_CREATE_FAILED + uploadDir);
                } else {
                    ImgpediaLogger.info(MessagesLogs.UPLOAD_DIR_CREATED + uploadDir);
                    cleanUploadDirectory(uploadPath);
                }
            } else {
                ImgpediaLogger.info(MessagesLogs.UPLOAD_DIR_EXISTING + uploadDir);
            }
        } catch (Exception e) {
            ImgpediaLogger.error(MessagesLogs.UPLOAD_SERVICE_INIT_ERROR + e.getMessage());
        }
    }

    /**
     * Processes an uploaded file and updates the progress status
     * @param file The uploaded file
     * @param uploadId Unique ID for the upload
     * @param targetFileName Temporary file name
     * @return true if processing was successful, false otherwise
     */
    public boolean processUploadedFile(MultipartFile file, String uploadId, String targetFileName) {
        File tempFile = null;

        try {
            updateUploadStatus(uploadId, "processing", MessagesLogs.PROCESSING_STARTING);
            updateUploadStatus(uploadId, "progress", 10);
            
            File uploadPath = new File(uploadDir);
            if (!createDirectoryIfNotExists(uploadPath, uploadId)) {
                return false;
            }
            
            tempFile = saveFileToDirectory(file, targetFileName, uploadId);
            if (tempFile == null) {
                return false;
            }
    
            boolean success = processFileBasedOnType(tempFile, targetFileName, uploadId);
            updateFinalStatus(success, uploadId, targetFileName);
            
            return success;
        } catch (Exception e) {
            String errorMsg = MessagesLogs.PROCESSING_ERROR + e.getMessage();
            ImgpediaLogger.error(errorMsg);
            updateUploadStatus(uploadId, "failed", errorMsg);
            return false;
        } finally {
            cleanupTempFile(tempFile);
        }
    }

    private boolean createDirectoryIfNotExists(File directory, String uploadId) {
        if (!directory.exists() && !directory.mkdirs()) {
            updateUploadStatus(uploadId, "failed", MessagesLogs.DIR_CREATE_ERROR);
            return false;
        }
        return true;
    }

    private File saveFileToDirectory(MultipartFile file, String targetFileName, String uploadId) {
        try {
            File tempFile = new File(uploadDir, targetFileName);
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                fos.write(file.getBytes());
            }
            updateUploadStatus(uploadId, "progress", 20);
            updateUploadStatus(uploadId, "processing", MessagesLogs.FILE_SAVED);
            return tempFile;
        } catch (Exception e) {
            updateUploadStatus(uploadId, "failed", MessagesLogs.FILE_SAVE_ERROR + e.getMessage());
            return null;
        }
    }

    private boolean processFileBasedOnType(File tempFile, String targetFileName, String uploadId) {
        if (targetFileName.endsWith(".tar.gz")) {
            ImgpediaLogger.info(MessagesLogs.PROCESSING_COMPRESSED_FILE + targetFileName);
            updateUploadStatus(uploadId, "processing", MessagesLogs.PROCESSING_COMPRESSED);
            return processCompressedFile(tempFile, uploadId);
        } else {
            ImgpediaLogger.info(MessagesLogs.PROCESSING_RDF_FILE + targetFileName);
            updateUploadStatus(uploadId, "processing", MessagesLogs.PROCESSING_RDF_FILE);
            updateUploadStatus(uploadId, "progress", 30);
            boolean success = processRdfFile(tempFile, uploadId);
            updateUploadStatus(uploadId, "progress", 90);
            return success;
        }
    }

    private void updateFinalStatus(boolean success, String uploadId, String targetFileName) {
        if (success) {
            updateUploadStatus(uploadId, "completed", 100);
            ImgpediaLogger.info(MessagesLogs.FILE_PROCESSED_SUCCESS + targetFileName);
        } else {
            updateUploadStatus(uploadId, "failed", MessagesLogs.PROCESSING_FAILED);
            ImgpediaLogger.warn(MessagesLogs.PROCESSING_FILE_ERROR + targetFileName);
        }
    }

    private void cleanupTempFile(File tempFile) {
        if (tempFile != null && tempFile.exists()) {
            if (tempFile.delete()) {
                ImgpediaLogger.info(MessagesLogs.TEMP_FILE_DELETED + tempFile.getName());
            } else {
                ImgpediaLogger.warn(MessagesLogs.TEMP_FILE_DELETE_FAILED + tempFile.getAbsolutePath());
            }
        }
    }

    private boolean processRdfFile(File file, String uploadId) {
        try (InputStream inputStream = new FileInputStream(file)) {
            ImgpediaLogger.info(MessagesLogs.LOADING_RDF_FILE + file.getAbsolutePath());
            updateUploadStatus(uploadId, "processing", MessagesLogs.LOADING_RDF);
            updateUploadStatus(uploadId, "progress", 40);
            
            return loadRdfIntoDataset(file, inputStream, uploadId);
        } catch (Exception e) {
            ImgpediaLogger.error(MessagesLogs.FILE_READ_ERROR + e.getMessage());
            updateUploadStatus(uploadId, "failed", MessagesLogs.FILE_READ_ERROR + e.getMessage());
            return false;
        }
    }

    private boolean loadRdfIntoDataset(File file, InputStream inputStream, String uploadId) {
        dataset.begin(ReadWrite.WRITE);
        try {
            updateUploadStatus(uploadId, "processing", MessagesLogs.PARSING_ENCODING);
            updateUploadStatus(uploadId, "progress", 50);
            
            Model tempModel = ModelFactory.createDefaultModel();
            
            RDFParser.create()
                    .source(inputStream)
                    .lang(RDFLanguages.filenameToLang(file.getName()))
                    .errorHandler(createErrorHandler())
                    .parse(tempModel);
            
            updateUploadStatus(uploadId, "processing", MessagesLogs.FILE_PARSED);
            updateUploadStatus(uploadId, "progress", 70);
            
            addTriplesToModel(tempModel, uploadId);
            
            dataset.commit();
            ImgpediaLogger.info(MessagesLogs.RDF_FILE_LOADED + file.getName());
            updateUploadStatus(uploadId, "processing", MessagesLogs.DATA_SAVED);
            updateUploadStatus(uploadId, "progress", 95);
            
            return true;
        } catch (Exception e) {
            if (dataset.isInTransaction()) {
                dataset.abort();
            }
            ImgpediaLogger.error(MessagesLogs.RDF_LOAD_ERROR + e.getMessage());
            updateUploadStatus(uploadId, "failed", MessagesLogs.RDF_LOAD_ERROR + e.getMessage());
            return false;
        } finally {
            if (dataset.isInTransaction()) {
                dataset.end();
            }
        }
    }

    private void addTriplesToModel(Model tempModel, String uploadId) {
        long totalTriples = tempModel.size();
        long triplesAdded = 0;
        int batchSize = 10000;
        
        StmtIterator stmtIter = tempModel.listStatements();
        while (stmtIter.hasNext()) {
            for (int i = 0; i < batchSize && stmtIter.hasNext(); i++) {
                model.add(stmtIter.next());
                triplesAdded++;
            }
            
            int progressPercent = (int)((triplesAdded * 20 / totalTriples) + 70); // 70% to 90%
            updateUploadStatus(uploadId, "progress", Math.min(progressPercent, 90));
            updateUploadStatus(uploadId, "processing", MessagesLogs.PROCESSING_TRIPLES + triplesAdded + "/" + totalTriples);
        }
    }

    private boolean processCompressedFile(File compressedFile, String uploadId) {
        ImgpediaLogger.info(MessagesLogs.PROCESSING_COMPRESSED_FILE + compressedFile.getAbsolutePath());
        boolean overallSuccess = true;
        
        File tempDir = new File(uploadDir, "temp_" + uploadId);
        if (!tempDir.exists() && !tempDir.mkdirs()) {
            ImgpediaLogger.error(MessagesLogs.TEMP_DIR_CREATE_ERROR + tempDir.getAbsolutePath());
            return false;
        }
        
        try {
            int[] fileCounts = countTarEntries(compressedFile);
            int totalEntries = fileCounts[0];
            
            if (totalEntries == 0) {
                ImgpediaLogger.warn(MessagesLogs.NO_VALID_ENTRIES);
                return false;
            }
            
            overallSuccess = processTarEntries(compressedFile, tempDir, totalEntries, uploadId);
        } catch (Exception e) {
            overallSuccess = false;
            ImgpediaLogger.error(MessagesLogs.COMPRESSED_FILE_ERROR + e.getMessage());
        } finally {
            deleteDirectory(tempDir);
        }
        
        return overallSuccess;
    }

    private int[] countTarEntries(File compressedFile) throws Exception {
        int validFiles = 0;
        
        try (FileInputStream fis = new FileInputStream(compressedFile);
             GzipCompressorInputStream gzis = new GzipCompressorInputStream(fis);
             TarArchiveInputStream tais = new TarArchiveInputStream(gzis)) {
            
            TarArchiveEntry entry;
            while ((entry = tais.getNextTarEntry()) != null) {
                if (!entry.isDirectory() && entry.getName().endsWith(".ttl")) {
                    validFiles++;
                }
            }
        }
        
        return new int[] { validFiles };
    }

    private boolean processTarEntries(File compressedFile, File tempDir, int totalEntries, String uploadId) throws Exception {
        boolean overallSuccess = true;
        int processedEntries = 0;
        
        try (FileInputStream fis = new FileInputStream(compressedFile);
             GzipCompressorInputStream gzis = new GzipCompressorInputStream(fis);
             TarArchiveInputStream tais = new TarArchiveInputStream(gzis)) {
            
            TarArchiveEntry entry;
            while ((entry = tais.getNextTarEntry()) != null) {
                if (entry.isDirectory() || !entry.getName().endsWith(".ttl")) {
                    continue;
                }
                
                File tempFile = extractEntryToTempFile(entry, tais, tempDir);
                if (tempFile != null) {
                    boolean entrySuccess = processTemporaryFile(tempFile, entry.getName());
                    if (!entrySuccess) {
                        overallSuccess = false;
                    }
                    
                    processedEntries++;
                    updateProgressForCompressedFile(uploadId, processedEntries, totalEntries);
                    
                    if (!tempFile.delete()) {
                        ImgpediaLogger.warn(MessagesLogs.TEMP_FILE_DELETE_WARNING + tempFile.getAbsolutePath());
                    }
                }
            }
        }
        
        return overallSuccess;
    }

    private File extractEntryToTempFile(TarArchiveEntry entry, TarArchiveInputStream tais, File tempDir) throws Exception {
        String entryName = entry.getName();
        String sanitizedName = entryName.replaceAll("[^a-zA-Z0-9.-]", "_");
        File tempFile = new File(tempDir, "tarentry_" + sanitizedName);
        
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            
            while ((bytesRead = tais.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
                if (bytesRead < buffer.length) {
                    break;
                }
            }
            
            return tempFile;
        } catch (Exception e) {
            ImgpediaLogger.error(MessagesLogs.ENTRY_EXTRACT_ERROR + entryName + ": " + e.getMessage());
            return null;
        }
    }

    private void updateProgressForCompressedFile(String uploadId, int processed, int total) {
        Map<String, Object> status = uploadStatus.get(uploadId);
        if (status != null) {
            int progress = (int)(((double)processed / total) * 100);
            status.put("progress", progress);
            status.put("processing", MessagesLogs.PROCESSING_ENTRY + processed + " of " + total);
        }
    }

    private boolean processTemporaryFile(File tempFile, String originalName) {
        dataset.begin(ReadWrite.WRITE);
        boolean success = false;
        
        try (InputStream inputStream = new FileInputStream(tempFile)) {
            ImgpediaLogger.info(MessagesLogs.LOADING_ENTRY + originalName);
        
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
            ImgpediaLogger.info(MessagesLogs.ENTRY_LOADED + originalName + " (" + statementsAdded + " statements)");
        } catch (Exception e) {
            ImgpediaLogger.error(MessagesLogs.ENTRY_LOAD_ERROR + e.getMessage());
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


    public void initializeUpload(String uploadId, String fileName) {
        Map<String, Object> statusInfo = new HashMap<>();
        statusInfo.put("status", "processing");
        statusInfo.put("fileName", fileName);
        statusInfo.put("startTime", System.currentTimeMillis());
        statusInfo.put("progress", 0);
        uploadStatus.put(uploadId, statusInfo);
        ImgpediaLogger.info(MessagesLogs.UPLOAD_INITIALIZED + uploadId);
    }

    public void updateUploadStatus(String uploadId, String status, Object details) {
        Map<String, Object> statusInfo = uploadStatus.getOrDefault(uploadId, new HashMap<>());
        statusInfo.put("status", status);
        statusInfo.put("lastUpdated", System.currentTimeMillis());
        
        if (details != null) {
            if ("progress".equals(status)) {
                statusInfo.put("progress", details);
            } else if ("failed".equals(status)) {
                statusInfo.put("error", details);
            } else if ("completed".equals(status)) {
                statusInfo.put("progress", 100);
                statusInfo.put("completedTime", System.currentTimeMillis());
                statusInfo.put("details", details);
            }
        }
        
        uploadStatus.put(uploadId, statusInfo);
    }
    
    public Map<String, Object> getUploadStatusById(String uploadId) {
        return uploadStatus.getOrDefault(uploadId, Map.of("status", "not_found"));
    }
}
