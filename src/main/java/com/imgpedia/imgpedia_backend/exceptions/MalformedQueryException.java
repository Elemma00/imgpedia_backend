package com.imgpedia.imgpedia_backend.exceptions;

public class MalformedQueryException extends RuntimeException{
    public MalformedQueryException(String message) {
        super(message);
    }
}
