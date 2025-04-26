package com.imgpedia.imgpedia_backend.logger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImgpediaLogger {

    private static final Logger logger = LoggerFactory.getLogger(ImgpediaLogger.class);

    private static String getCallerInfo() {
        StackTraceElement caller = Thread.currentThread().getStackTrace()[3];
        return String.format("Class: %s, Method: %s, Line: %d",
                caller.getClassName(), caller.getMethodName(), caller.getLineNumber());
    }

    public static void info(String message) {
        logger.info(getCallerInfo() + " - " + message);
    }

    public static void error(String message) {
        logger.error(getCallerInfo() + " - " + message);
    }

    public static void warn(String message) {
        logger.warn(getCallerInfo() + " - " + message);
    }

    public static void logRequest(String method, String uri, String params, String body) {
        logger.info(getCallerInfo() + String.format(" - Incoming request: method=%s, uri=%s, params=%s, body=%s",
                method, uri, params, body));
    }

    public static void logResponse(int status, String body) {
        logger.info(getCallerInfo() + String.format(" - Outgoing response: status=%d, body=%s",
                status, body));
    }
}
