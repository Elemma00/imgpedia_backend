package com.imgpedia.imgpedia_backend.services;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.imgpedia.imgpedia_backend.dataload.EncodeIRI;
import com.imgpedia.imgpedia_backend.logger.ImgpediaLogger;
import com.imgpedia.imgpedia_backend.utils.MessagesLogs;
import static com.imgpedia.imgpedia_backend.utils.UploadRdfUtil.cleanUploadDirectory;
import static com.imgpedia.imgpedia_backend.utils.UploadRdfUtil.createErrorHandler;
import static com.imgpedia.imgpedia_backend.utils.UploadRdfUtil.deleteDirectory;

import jakarta.annotation.PostConstruct;

/**
 * Service for handling RDF file uploads and processing.
 * This service manages the upload, processing, and storage of RDF files.
 */
@Service
public class RdfUploadService {

    @Autowired
    @Qualifier("rdfDataset")
    private Dataset dataset;

    @Autowired
    @Qualifier("rdfModel")
    private Model model;

    @Autowired
    private UserService userService;

    private static final String UPLOAD_DIR = "/imgpedia/temp_extraction";
    private static final int BATCH_SIZE = 10000;

    private final Map<String, Map<String, Object>> uploadStatus = new ConcurrentHashMap<>();

    public RdfUploadService() {}

    /**
     * Initializes the upload directory after bean construction.
     */
    @PostConstruct
    public void init() {
        try {
            File uploadPath = new File(UPLOAD_DIR);
            if (!uploadPath.exists() && !uploadPath.mkdirs()) {
                ImgpediaLogger.error(MessagesLogs.UPLOAD_DIR_CREATE_FAILED + UPLOAD_DIR);
            } else {
                ImgpediaLogger.info(MessagesLogs.UPLOAD_DIR_CREATED + UPLOAD_DIR);
                cleanUploadDirectory(uploadPath);
            }
        } catch (Exception e) {
            ImgpediaLogger.error(MessagesLogs.UPLOAD_SERVICE_INIT_ERROR + e.getMessage());
        }
    }

    /**
     * Processes an uploaded file and updates the progress status.
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

            File uploadPath = new File(UPLOAD_DIR);
            if (!ensureDirectoryExists(uploadPath, uploadId)) {
                return false;
            }

            tempFile = saveFileToDirectory(file, targetFileName, uploadId);
            if (tempFile == null) {
                return false;
            }

            boolean success = processFileByType(tempFile, targetFileName, uploadId);
            updateFinalStatus(success, uploadId, targetFileName);

            return success;
        } catch (Exception e) {
            String errorMsg = MessagesLogs.PROCESSING_ERROR + e.getMessage();
            ImgpediaLogger.error(errorMsg);
            updateUploadStatus(uploadId, "failed", errorMsg);
            return false;
        } finally {
            deleteTempFile(tempFile);
        }
    }

    /**
     * Ensures the directory exists, creating it if necessary.
     */
    private boolean ensureDirectoryExists(File directory, String uploadId) {
        if (!directory.exists() && !directory.mkdirs()) {
            updateUploadStatus(uploadId, "failed", MessagesLogs.DIR_CREATE_ERROR);
            return false;
        }
        return true;
    }

    /**
     * Saves the uploaded file to the target directory.
     */
    private File saveFileToDirectory(MultipartFile file, String targetFileName, String uploadId) {
        try {
            File tempFile = new File(UPLOAD_DIR, targetFileName);
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

    /**
     * Processes the file based on its type (compressed or RDF).
     */
    private boolean processFileByType(File tempFile, String targetFileName, String uploadId) {
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

    /**
     * Updates the final status of the upload.
     */
    private void updateFinalStatus(boolean success, String uploadId, String targetFileName) {
        if (success) {
            updateUploadStatus(uploadId, "completed", 100);
            ImgpediaLogger.info(MessagesLogs.FILE_PROCESSED_SUCCESS + targetFileName);
        } else {
            updateUploadStatus(uploadId, "failed", MessagesLogs.PROCESSING_FAILED);
            ImgpediaLogger.warn(MessagesLogs.PROCESSING_FILE_ERROR + targetFileName);
        }
    }

    /**
     * Deletes the temporary file if it exists.
     */
    private void deleteTempFile(File tempFile) {
        if (tempFile != null && tempFile.exists() && !tempFile.delete()) {
            ImgpediaLogger.warn(MessagesLogs.TEMP_FILE_DELETE_FAILED + tempFile.getAbsolutePath());
        }
    }

    /**
     * Processes a single RDF file and loads its content into the dataset.
     */
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

    /**
     * Loads RDF data into the dataset within a transaction.
     */
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
                    .parse(new EncodeIRI(tempModel));

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

    /**
     * Adds triples from a temporary model to the main model in batches.
     */
    private void addTriplesToModel(Model tempModel, String uploadId) {
        long totalTriples = tempModel.size();
        long triplesAdded = 0;

        StmtIterator stmtIter = tempModel.listStatements();
        while (stmtIter.hasNext()) {
            for (int i = 0; i < BATCH_SIZE && stmtIter.hasNext(); i++) {
                model.add(stmtIter.next());
                triplesAdded++;
            }
            int progressPercent = (int) ((triplesAdded * 20 / totalTriples) + 70); // 70% to 90%
            updateUploadStatus(uploadId, "progress", Math.min(progressPercent, 90));
            updateUploadStatus(uploadId, "processing", MessagesLogs.PROCESSING_TRIPLES + triplesAdded + "/" + totalTriples);
        }
    }

    /**
     * Processes a compressed tar.gz file containing RDF files.
     */
    private boolean processCompressedFile(File compressedFile, String uploadId) {
        ImgpediaLogger.info(MessagesLogs.PROCESSING_COMPRESSED_FILE + compressedFile.getAbsolutePath());
        boolean overallSuccess = true;

        File tempDir = new File(UPLOAD_DIR, "temp_" + uploadId);
        if (!tempDir.exists() && !tempDir.mkdirs()) {
            ImgpediaLogger.error(MessagesLogs.TEMP_DIR_CREATE_ERROR + tempDir.getAbsolutePath());
            return false;
        }

        try {
            int totalEntries = countTarEntries(compressedFile);
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

    /**
     * Counts the number of valid RDF files in the tar.gz archive.
     */
    private int countTarEntries(File compressedFile) throws Exception {
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
        return validFiles;
    }

    /**
     * Processes each RDF file entry in the tar.gz archive.
     */
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

    /**
     * Extracts a tar entry to a temporary file.
     */
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

    /**
     * Updates the progress for compressed file processing.
     */
    private void updateProgressForCompressedFile(String uploadId, int processed, int total) {
        Map<String, Object> status = uploadStatus.get(uploadId);
        if (status != null) {
            int progress = (int) (((double) processed / total) * 100);
            status.put("progress", progress);
            status.put("processing", MessagesLogs.PROCESSING_ENTRY + processed + " of " + total);
        }
    }

    /**
     * Processes a temporary RDF file extracted from the archive.
     */
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

            int statementsAdded = 0;
            StmtIterator stmtIter = tempModel.listStatements();
            while (stmtIter.hasNext()) {
                for (int i = 0; i < BATCH_SIZE && stmtIter.hasNext(); i++) {
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

    /**
     * Initializes the upload status for a new upload.
     * @param uploadId The unique upload identifier
     * @param fileName The file name being uploaded
     */
    public void initializeUpload(String uploadId, String fileName) {
        Map<String, Object> statusInfo = new HashMap<>();
        statusInfo.put("status", "processing");
        statusInfo.put("fileName", fileName);
        statusInfo.put("startTime", System.currentTimeMillis());
        statusInfo.put("progress", 0);
        uploadStatus.put(uploadId, statusInfo);
        ImgpediaLogger.info(MessagesLogs.UPLOAD_INITIALIZED + uploadId);
    }

    /**
     * Updates the upload status for a given upload ID.
     * @param uploadId The upload identifier
     * @param status The new status
     * @param details Additional details or progress
     */
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

    /**
     * Gets the status of all uploads currently tracked by the service.
     * @return A map containing all upload statuses categorized by their state.
     */
    public Map<String, Object> getAllUploadStatuses() {
        Map<String, Object> allStatuses = new HashMap<>();
        Map<String, Object> activeUploads = new HashMap<>();
        Map<String, Object> completedUploads = new HashMap<>();
        Map<String, Object> failedUploads = new HashMap<>();

        uploadStatus.forEach((id, status) -> {
            String statusValue = (String) status.get("status");
            if ("processing".equals(statusValue)) {
                activeUploads.put(id, status);
            } else if ("completed".equals(statusValue)) {
                completedUploads.put(id, status);
            } else if ("failed".equals(statusValue)) {
                failedUploads.put(id, status);
            }
        });

        allStatuses.put("total", uploadStatus.size());
        allStatuses.put("active", activeUploads);
        allStatuses.put("completed", completedUploads);
        allStatuses.put("failed", failedUploads);

        return allStatuses;
    }

    /**
     * Cleans up old upload statuses every hour.
     */
    @Scheduled(fixedRate = 3600000)
    public void cleanupOldUploadStatuses() {
        long now = System.currentTimeMillis();
        long maxAge = 24 * 60 * 60 * 1000; // 24 hours
        List<String> idsToRemove = new ArrayList<>();

        uploadStatus.forEach((id, status) -> {
            String statusValue = (String) status.get("status");
            if ("completed".equals(statusValue) || "failed".equals(statusValue)) {
                Long lastUpdated = (Long) status.get("lastUpdated");
                if (lastUpdated != null && (now - lastUpdated > maxAge)) {
                    idsToRemove.add(id);
                }
            }
        });

        for (String id : idsToRemove) {
            uploadStatus.remove(id);
        }

        if (!idsToRemove.isEmpty()) {
            ImgpediaLogger.info("Cleaned up " + idsToRemove.size() + " old upload status records");
        }
    }

    /**
     * Gets the upload status for a specific upload ID.
     * @param uploadId The upload identifier
     * @return The status map for the upload, or a not_found status if not present
     */
    public Map<String, Object> getUploadStatusById(String uploadId) {
        return uploadStatus.getOrDefault(uploadId, Map.of("status", "not_found"));
    }

    /**
     * Gets the user service.
     * @return The user service
     */
    public UserService getUserService() {
        return userService;
    }
}
