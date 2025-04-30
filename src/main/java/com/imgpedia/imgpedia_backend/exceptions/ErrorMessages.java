package com.imgpedia.imgpedia_backend.exceptions;

public class ErrorMessages {
    
    // Query Errors
    public static final String INVALID_QUERY_SYNTAX = "The query syntax is invalid.";
    public static final String QUERY_EXECUTION_FAILED = "Failed to execute the query.";
    public static final String QUERY_TIMEOUT = "The query execution timed out.";
    public static final String QUERY_NO_RESULTS = "The query did not return any results.";

    // Upload Errors
    public static final String UPLOAD_FILE_EMPTY = "The uploaded file is empty.";
    public static final String UPLOAD_FILE_NOT_SUPPORTED = "File type not supported. Allowed formats: .ttl, .rdf, .nt, .tar.gz";
    public static final String UPLOAD_FILE_PROCESSING_FAILED = "Failed to process the uploaded file, try again.";
    public static final String UPLOAD_DIR_CANT_CREATE = "Could not create upload directory.";
}
