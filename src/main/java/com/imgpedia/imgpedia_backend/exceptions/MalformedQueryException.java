package com.imgpedia.imgpedia_backend.exceptions;

/**
 * Custom exception class for handling malformed queries.
 * This exception is thrown when a query does not conform to the expected format or structure.
 */
public class MalformedQueryException extends RuntimeException{
    public MalformedQueryException(String message) {
        super(message);
    }
}
