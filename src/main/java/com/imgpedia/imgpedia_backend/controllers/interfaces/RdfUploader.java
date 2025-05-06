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
     * Gets the status of an RDF upload.
     * @param uploadId The ID of the upload to check the status of.
     * @return A ResponseEntity containing the status of the upload.
     */
    @Operation(summary = "Get status of RDF upload")
    @GetMapping("/status")
    ResponseEntity<?> getUploadStatus(@RequestParam("uploadId") String uploadId);
}
