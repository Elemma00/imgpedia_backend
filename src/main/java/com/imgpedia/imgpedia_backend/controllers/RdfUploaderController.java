package com.imgpedia.imgpedia_backend.controllers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.imgpedia.imgpedia_backend.controllers.interfaces.RdfUploader;
import com.imgpedia.imgpedia_backend.logger.ImgpediaLogger;
import com.imgpedia.imgpedia_backend.models.auth.User;
import com.imgpedia.imgpedia_backend.services.RdfUploadService;
import com.imgpedia.imgpedia_backend.utils.MessagesLogs;
import static com.imgpedia.imgpedia_backend.utils.UploadRdfUtil.getFileExtension;
import static com.imgpedia.imgpedia_backend.utils.UploadRdfUtil.isValidRdfFile;

/**
 * Controller for handling RDF file uploads and status queries.
 */
@RestController
@RequestMapping("api/data")
public class RdfUploaderController implements RdfUploader {

    @Autowired
    private RdfUploadService rdfUploadService;

    /**
     * Handles single RDF file upload.
     *
     * @param file Multipart file to upload
     * @return ResponseEntity with upload status
     */
    @Override
    public ResponseEntity<?> uploadRdfData(MultipartFile file) {
        User user = getAuthenticatedUser();
        if (!isUserEnabled(user)) {
            return forbiddenResponse("Your account is disabled and cannot upload files");
        }
        if (file == null || file.isEmpty()) {
            return badRequestResponse(MessagesLogs.UPLOAD_FILE_EMPTY);
        }
        String originalFileName = file.getOriginalFilename();
        if (!isValidRdfFile(getFileExtension(originalFileName))) {
            return badRequestResponse(MessagesLogs.UPLOAD_FILE_NOT_SUPPORTED);
        }
        try {
            String uploadId = generateUploadId();
            String targetFileName = buildTargetFileName(uploadId, originalFileName);
            rdfUploadService.initializeUpload(uploadId, originalFileName);
            processFileAsync(file, uploadId, targetFileName, originalFileName);
            return ResponseEntity.accepted().body(Map.of(
                "message", "File upload initiated, processing in background",
                "uploadId", uploadId,
                "status", "processing"
            ));
        } catch (Exception e) {
            ImgpediaLogger.error(e.getMessage());
            return internalServerErrorResponse(e.getMessage());
        }
    }

    /**
     * Handles multiple RDF file uploads.
     *
     * @param files Array of multipart files to upload
     * @return ResponseEntity with upload statuses
     */
    @Override
    public ResponseEntity<?> uploadMultipleRdfData(MultipartFile[] files) {
        User user = getAuthenticatedUser();
        if (!isUserEnabled(user)) {
            return forbiddenResponse("Your account is disabled and cannot upload files");
        }
        if (files == null || files.length == 0) {
            return badRequestResponse(MessagesLogs.UPLOAD_FILE_EMPTY);
        }
        List<Map<String, Object>> results = new ArrayList<>();
        for (MultipartFile file : files) {
            results.add(handleSingleFileUpload(file));
        }
        return ResponseEntity.accepted().body(Map.of(
            "message", "Multiple file uploads initiated",
            "count", files.length,
            "uploads", results
        ));
    }

    /**
     * Gets the status of a single upload by ID.
     *
     * @param uploadId Upload identifier
     * @return ResponseEntity with upload status
     */
    @Override
    public ResponseEntity<?> getUploadStatus(String uploadId) {
        Map<String, Object> status = rdfUploadService.getUploadStatusById(uploadId);
        if ("not_found".equals(status.get("status"))) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", "Upload ID not found"));
        }
        return ResponseEntity.ok(status);
    }

    /**
     * Gets the statuses of multiple uploads by comma-separated IDs.
     *
     * @param uploadIds Comma-separated upload IDs
     * @return ResponseEntity with batch upload statuses
     */
    @Override
    public ResponseEntity<?> getBatchUploadStatus(String uploadIds) {
        if (uploadIds == null || uploadIds.trim().isEmpty()) {
            return badRequestResponse("No upload IDs provided");
        }
        String[] idArray = uploadIds.split(",");
        Map<String, Object> batchStatus = new HashMap<>();
        for (String id : idArray) {
            String trimmedId = id.trim();
            if (!trimmedId.isEmpty()) {
                Map<String, Object> status = rdfUploadService.getUploadStatusById(trimmedId);
                batchStatus.put(trimmedId, status);
            }
        }
        return ResponseEntity.ok(Map.of(
            "count", batchStatus.size(),
            "statuses", batchStatus
        ));
    }

    /**
     * Gets the statuses of all uploads.
     *
     * @return ResponseEntity with all upload statuses
     */
    @Override
    public ResponseEntity<?> getAllUploadStatuses() {
        return ResponseEntity.ok(rdfUploadService.getAllUploadStatuses());
    }

    // --- Private helper methods ---

    /**
     * Retrieves the currently authenticated user.
     */
    private User getAuthenticatedUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return rdfUploadService.getUserService().findByUsername(username).orElse(null);
    }

    /**
     * Checks if the user is enabled.
     */
    private boolean isUserEnabled(User user) {
        return user != null && user.isEnabled();
    }

    /**
     * Generates a unique upload ID.
     */
    private String generateUploadId() {
        return java.util.UUID.randomUUID().toString();
    }

    /**
     * Builds the target file name for storage.
     */
    private String buildTargetFileName(String uploadId, String originalFileName) {
        return uploadId + "_" + originalFileName;
    }

    /**
     * Processes a file asynchronously.
     */
    private void processFileAsync(MultipartFile file, String uploadId, String targetFileName, String originalFileName) {
        CompletableFuture.runAsync(() -> {
            try {
                ImgpediaLogger.info(MessagesLogs.UPLOADING_STARTED + originalFileName);
                rdfUploadService.processUploadedFile(file, uploadId, targetFileName);
            } catch (Exception e) {
                ImgpediaLogger.error(MessagesLogs.PROCESSING_ERROR + e.getMessage());
                rdfUploadService.updateUploadStatus(uploadId, "failed", e.getMessage());
            }
        });
    }

    /**
     * Handles the upload of a single file and returns its result map.
     */
    private Map<String, Object> handleSingleFileUpload(MultipartFile file) {
        Map<String, Object> fileResult = new HashMap<>();
        String originalFileName = file.getOriginalFilename();
        fileResult.put("fileName", originalFileName);

        if (file == null || file.isEmpty()) {
            fileResult.put("status", "rejected");
            fileResult.put("reason", MessagesLogs.UPLOAD_FILE_EMPTY);
            return fileResult;
        }
        if (!isValidRdfFile(getFileExtension(originalFileName))) {
            fileResult.put("status", "rejected");
            fileResult.put("reason", MessagesLogs.UPLOAD_FILE_NOT_SUPPORTED);
            return fileResult;
        }
        try {
            String uploadId = generateUploadId();
            String targetFileName = buildTargetFileName(uploadId, originalFileName);
            rdfUploadService.initializeUpload(uploadId, originalFileName);
            fileResult.put("status", "processing");
            fileResult.put("uploadId", uploadId);
            processFileAsync(file, uploadId, targetFileName, originalFileName);
        } catch (Exception e) {
            fileResult.put("status", "failed");
            fileResult.put("reason", e.getMessage());
            ImgpediaLogger.error(e.getMessage());
        }
        return fileResult;
    }

    /**
     * Returns a 400 Bad Request response with a message.
     */
    private ResponseEntity<Map<String, String>> badRequestResponse(String message) {
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }

    /**
     * Returns a 403 Forbidden response with a message.
     */
    private ResponseEntity<Map<String, String>> forbiddenResponse(String message) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", message));
    }

    /**
     * Returns a 500 Internal Server Error response with a message.
     */
    private ResponseEntity<Map<String, String>> internalServerErrorResponse(String message) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", message));
    }
}