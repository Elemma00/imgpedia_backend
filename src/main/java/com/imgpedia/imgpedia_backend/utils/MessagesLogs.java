package com.imgpedia.imgpedia_backend.utils;

public class MessagesLogs {
    
    // Query Errors
    public static final String INVALID_QUERY_SYNTAX = "The query syntax is invalid.";
    public static final String QUERY_EXECUTION_FAILED = "Failed to execute the query.";
    public static final String QUERY_TIMEOUT = "The query execution timed out.";
    public static final String QUERY_NO_RESULTS = "The query did not return any results.";
    public static final String QUERY_DEFAULT_ERROR = "An error occurred while executing the query: ";
    public static final String QUERY_STOP_ERROR = "An error occurred while stopping the query: ";

    // Query Info Messages
    public static final String QUERY_EXECUTED_SUCCESS = "Query executed successfully: ";
    public static final String QUERY_STOPPED = "Query execution stopped successfully: ";

    // Upload Errors
    public static final String UPLOAD_FILE_EMPTY = "The uploaded file is empty.";
    public static final String UPLOAD_FILE_NOT_SUPPORTED = "File type not supported. Allowed formats: .ttl, .rdf, .nt, .tar.gz";
    public static final String UPLOAD_FILE_PROCESSING_FAILED = "Failed to process the uploaded file, try again.";
    public static final String UPLOAD_DIR_CANT_CREATE = "Could not create upload directory.";
    
    // RDF Upload Service Info Messages
    public static final String UPLOAD_DIR_CREATED = "Upload directory created: ";
    public static final String UPLOAD_DIR_EXISTING = "Using existing upload directory: ";
    public static final String UPLOAD_INITIALIZED = "Upload initialized with ID: ";
    public static final String FILE_PROCESSED_SUCCESS = "File processed successfully: ";
    public static final String TEMP_FILE_DELETED = "Temporary file deleted: ";
    public static final String RDF_FILE_LOADED = "Successfully loaded RDF file: ";
    public static final String ENTRY_LOADED = "Successfully loaded entry: ";
    
    // RDF Upload Service Error Messages
    public static final String UPLOAD_DIR_CREATE_FAILED = "Could not create upload directory: ";
    public static final String UPLOAD_SERVICE_INIT_ERROR = "Error initializing upload service: ";
    public static final String PROCESSING_ERROR = "Error during processing: ";
    public static final String DIR_CREATE_ERROR = "Could not create directory for file";
    public static final String FILE_SAVE_ERROR = "Failed to save file: ";
    public static final String FILE_READ_ERROR = "Error reading file: ";
    public static final String RDF_LOAD_ERROR = "Error loading RDF file: ";
    public static final String COMPRESSED_FILE_ERROR = "Error processing compressed file: ";
    public static final String TEMP_DIR_CREATE_ERROR = "Failed to create temp directory: ";
    public static final String ENTRY_EXTRACT_ERROR = "Failed to extract entry ";
    public static final String ENTRY_LOAD_ERROR = "Error loading entry: ";
    
    // RDF Upload Service Warning Messages
    public static final String TEMP_FILE_DELETE_FAILED = "Could not delete temporary file: ";
    public static final String NO_VALID_ENTRIES = "No valid entries found in archive";
    public static final String PROCESSING_FILE_ERROR = "Error processing file: ";
    public static final String TEMP_FILE_DELETE_WARNING = "Failed to delete temp file: ";
    
    // RDF Upload Service Processing Messages
    public static final String UPLOADING_STARTED = "Starting asynchronous processing of file: ";
    public static final String PROCESSING_COMPRESSED_FILE = "Processing compressed file: ";
    public static final String PROCESSING_RDF_FILE = "Processing RDF file: ";
    public static final String LOADING_RDF_FILE = "Loading RDF file: ";
    public static final String LOADING_ENTRY = "Loading entry from temp file: ";
    public static final String PROCESSING_STARTING = "Starting processing";
    public static final String FILE_SAVED = "File saved, preparing upload";
    public static final String PROCESSING_COMPRESSED = "Processing compressed file";
    public static final String LOADING_RDF = "Loading RDF file";
    public static final String PARSING_ENCODING = "Parsing and encoding IRIs";
    public static final String FILE_PARSED = "File parsed, adding triples to main model";
    public static final String DATA_SAVED = "Data saved to model";
    public static final String PROCESSING_COMPLETED = "Processing completed successfully";
    public static final String PROCESSING_FAILED = "Error during processing";
    public static final String PROCESSING_TRIPLES = "Processing triples: ";
    public static final String PROCESSING_ENTRY = "Processing entry ";
    public static final String MULTIPLE_UPLOADS_STARTED = "Multiple file uploads initiated: ";
    public static final String BATCH_PROCESSING = "Processing batch of files: ";


    
}
