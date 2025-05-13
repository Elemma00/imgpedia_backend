package com.imgpedia.imgpedia_backend.controllers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

@RestController
@RequestMapping("api/data")
public class RdfUploaderController implements RdfUploader {

    @Autowired
    private RdfUploadService rdfUploadService;

   @Override
    public ResponseEntity<?> uploadRdfData(MultipartFile file) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = rdfUploadService.getUserService().findByUsername(username)
            .orElse(null);
        if (user == null || !user.isEnabled()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "Your account is disabled and cannot upload files"));
        }

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(MessagesLogs.UPLOAD_FILE_EMPTY);
        }

        String originalFileName = file.getOriginalFilename();
        String fileExtension = getFileExtension(originalFileName);

        if (!isValidRdfFile(fileExtension)) {
            return ResponseEntity.badRequest().body(MessagesLogs.UPLOAD_FILE_NOT_SUPPORTED);
        }

        try {
            String uploadId = java.util.UUID.randomUUID().toString();
            String targetFileName = uploadId + "_" + originalFileName;

            rdfUploadService.initializeUpload(uploadId, originalFileName);

            CompletableFuture.runAsync(() -> {
                try {
                    ImgpediaLogger.info(MessagesLogs.UPLOADING_STARTED + originalFileName);
                    rdfUploadService.processUploadedFile(file, uploadId, targetFileName);
                } catch (Exception e) {
                    ImgpediaLogger.error(MessagesLogs.PROCESSING_ERROR + e.getMessage());
                    rdfUploadService.updateUploadStatus(uploadId, "failed", e.getMessage());
                }
            });

            return ResponseEntity.accepted().body(Map.of(
                "message", "File upload initiated, processing in background",
                "uploadId", uploadId,
                "status", "processing"
            ));

        } catch (Exception e) {
            ImgpediaLogger.error(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(e.getMessage());
        }
    }

    @Override
    public ResponseEntity<?> uploadMultipleRdfData(MultipartFile[] files) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = rdfUploadService.getUserService().findByUsername(username)
            .orElse(null);
        if (user == null || !user.isEnabled()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "Your account is disabled and cannot upload files"));
        }

        if (files == null || files.length == 0) {
            return ResponseEntity.badRequest().body(MessagesLogs.UPLOAD_FILE_EMPTY);
        }

        List<Map<String, Object>> results = new ArrayList<>();

        for (MultipartFile file : files) {
            Map<String, Object> fileResult = new HashMap<>();
            fileResult.put("fileName", file.getOriginalFilename());

            if (file.isEmpty()) {
                fileResult.put("status", "rejected");
                fileResult.put("reason", MessagesLogs.UPLOAD_FILE_EMPTY);
                results.add(fileResult);
                continue;
            }

            String originalFileName = file.getOriginalFilename();
            String fileExtension = getFileExtension(originalFileName);

            if (!isValidRdfFile(fileExtension)) {
                fileResult.put("status", "rejected");
                fileResult.put("reason", MessagesLogs.UPLOAD_FILE_NOT_SUPPORTED);
                results.add(fileResult);
                continue;
            }

            try {
                String uploadId = java.util.UUID.randomUUID().toString();
                String targetFileName = uploadId + "_" + originalFileName;

                rdfUploadService.initializeUpload(uploadId, originalFileName);

                fileResult.put("status", "processing");
                fileResult.put("uploadId", uploadId);
                results.add(fileResult);

                CompletableFuture.runAsync(() -> {
                    try {
                        ImgpediaLogger.info(MessagesLogs.UPLOADING_STARTED + originalFileName);
                        rdfUploadService.processUploadedFile(file, uploadId, targetFileName);
                    } catch (Exception e) {
                        ImgpediaLogger.error(MessagesLogs.PROCESSING_ERROR + e.getMessage());
                        rdfUploadService.updateUploadStatus(uploadId, "failed", e.getMessage());
                    }
                });

            } catch (Exception e) {
                fileResult.put("status", "failed");
                fileResult.put("reason", e.getMessage());
                results.add(fileResult);
                ImgpediaLogger.error(e.getMessage());
            }
        }

        return ResponseEntity.accepted().body(Map.of(
            "message", "Multiple file uploads initiated",
            "count", files.length,
            "uploads", results
        ));
    }

    @Override
    public ResponseEntity<?> getUploadStatus(String uploadId) {
        Map<String, Object> status = rdfUploadService.getUploadStatusById(uploadId);
        if ("not_found".equals(status.get("status"))) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", "Upload ID not found"));
        }
        return ResponseEntity.ok(status);
    }
    
    @Override
    public ResponseEntity<?> getBatchUploadStatus(String uploadIds) {
        if (uploadIds == null || uploadIds.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "No upload IDs provided"));
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
    
    @Override
    public ResponseEntity<?> getAllUploadStatuses() {
        return ResponseEntity.ok(rdfUploadService.getAllUploadStatuses());
    }
}