package com.imgpedia.imgpedia_backend.controllers.interfaces;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "RDF Upload API", description = "API for uploading RDF data")
public interface RdfUploaderApiController {

    @Operation(summary = "Upload RDF data")
    @PostMapping("/upload")
    ResponseEntity<?> uploadRdfData(@RequestParam("file") MultipartFile file);

    @Operation(summary = "Upload RDF data from URL")
    @PostMapping("/upload/url")
    ResponseEntity<?> uploadRdfDataFromUrl(@RequestParam("url") String url, @RequestParam("format") String format);
                                
    @Operation(summary = "Get status of RDF upload")
    @GetMapping("/status")
    ResponseEntity<?> getUploadStatus(@RequestParam("uploadId") String uploadId);
}
