package com.imgpedia.imgpedia_backend.controllers.interfaces;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * RdfUploader is an interface that defines the API for uploading RDF data.
 * It contains methods for uploading RDF files and checking the status of the upload.
 */
@Tag(name = "RDF Upload API", description = "API for uploading RDF data")
public interface RdfUploader {

    /**
     * Uploads RDF data from a file.
     * @param file The RDF file to be uploaded.
     * @return A ResponseEntity containing the result of the upload operation.
     */
    @Operation(summary = "Upload RDF data")
    @PostMapping("/upload")
    ResponseEntity<?> uploadRdfData(@RequestParam("file") MultipartFile file);
    
     /**
     * Uploads multiple RDF files simultaneously.
     * @param files Array of RDF files to be uploaded.
     * @return A ResponseEntity containing the result of the multiple upload operation.
     */
    @Operation(summary = "Upload multiple RDF files")
    @PostMapping("/upload-multiple")
    ResponseEntity<?> uploadMultipleRdfData(@RequestParam("files") MultipartFile[] files);
    

    /**
     * Gets the status of an RDF upload.
     * @param uploadId The ID of the upload to check the status of.
     * @return A ResponseEntity containing the status of the upload.
     */
    @Operation(summary = "Get status of RDF upload")
    @GetMapping("/status")
    ResponseEntity<?> getUploadStatus(@RequestParam("uploadId") String uploadId);


     /**
     * Gets the status of multiple RDF uploads.
     * @param uploadIds Comma-separated list of upload IDs to check.
     * @return A ResponseEntity containing the status of all requested uploads.
     */
    @Operation(summary = "Get status of multiple RDF uploads")
    @GetMapping("/status-batch")
    ResponseEntity<?> getBatchUploadStatus(@RequestParam("uploadIds") String uploadIds);
    
    /**
     * Gets the status of all active RDF uploads.
     * @return A ResponseEntity containing the status of all current uploads.
     */
    @Operation(summary = "Get status of all RDF uploads")
    @GetMapping("/status-all")
    ResponseEntity<?> getAllUploadStatuses();

}
