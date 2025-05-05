package com.imgpedia.imgpedia_backend.controllers;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.imgpedia.imgpedia_backend.controllers.interfaces.RdfUploaderApiController;
import com.imgpedia.imgpedia_backend.logger.ImgpediaLogger;
import com.imgpedia.imgpedia_backend.services.RdfUploadService;
import com.imgpedia.imgpedia_backend.utils.MessagesLogs;
import static com.imgpedia.imgpedia_backend.utils.UploadRdfUtil.getFileExtension;
import static com.imgpedia.imgpedia_backend.utils.UploadRdfUtil.isValidRdfFile;


@RestController
@RequestMapping("api/data")
public class RdfUploaderController implements RdfUploaderApiController {

    @Autowired
    private RdfUploadService rdfUploadService;

    @Override
    public ResponseEntity<?> uploadRdfData(MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(MessagesLogs.UPLOAD_FILE_EMPTY);
        }

        String originalFileName = file.getOriginalFilename();
        String fileExtension = getFileExtension(originalFileName);
        
        if (!isValidRdfFile(fileExtension)) {
            return ResponseEntity.badRequest().body(MessagesLogs.UPLOAD_FILE_NOT_SUPPORTED);
        }

        try {
         
            String uploadId = UUID.randomUUID().toString();
            String targetFileName = uploadId + "_" + originalFileName;
            
   
            rdfUploadService.initializeUpload(uploadId, originalFileName);
            
            CompletableFuture.runAsync(() -> {
                try {
                    ImgpediaLogger.info("Starting asynchronous processing of file: " + originalFileName);
                    rdfUploadService.processUploadedFile(file, uploadId, targetFileName);
                } catch (Exception e) {
                    ImgpediaLogger.error("Error during async file processing: " + e.getMessage());
                    rdfUploadService.updateUploadStatus(uploadId, "failed", e.getMessage());
                }
            });
            
            // Devolver inmediatamente el ID de carga al cliente
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
    public ResponseEntity<?> uploadRdfDataFromUrl(String url, String format) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'uploadRdfDataFromUrl'");
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

}
