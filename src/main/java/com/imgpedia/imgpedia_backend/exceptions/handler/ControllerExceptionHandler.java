package com.imgpedia.imgpedia_backend.exceptions.handler;

import java.util.concurrent.TimeoutException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import com.imgpedia.imgpedia_backend.exceptions.MalformedQueryException;
import com.imgpedia.imgpedia_backend.logger.ImgpediaLogger;

@ControllerAdvice
public class ControllerExceptionHandler {

    @ExceptionHandler(MalformedQueryException.class)
    public ResponseEntity<?> handlerMalformedQueryException(MalformedQueryException e, WebRequest request) {
        ImgpediaLogger.logError(e.getMessage());
        return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(TimeoutException.class)
    public ResponseEntity<?> handlerTimeoutException(TimeoutException e, WebRequest request){
        ImgpediaLogger.logError(e.getMessage());
        return new ResponseEntity<>(e.getMessage(), HttpStatus.REQUEST_TIMEOUT);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<?> handlerRunTimeException(RuntimeException e, WebRequest request) {
        ImgpediaLogger.logError(e.getMessage());
        return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handlerException(Exception e, WebRequest request) {
        ImgpediaLogger.logError(e.getMessage());
        return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
    }

}
