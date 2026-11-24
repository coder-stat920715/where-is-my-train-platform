package com.wimt.query.model;

/** Thrown by Resilience4j fallbacks when the read store is unavailable and no cached copy exists. */
public class ServiceDegradedException extends RuntimeException {
    public ServiceDegradedException(String message, Throwable cause) {
        super(message, cause);
    }
}
