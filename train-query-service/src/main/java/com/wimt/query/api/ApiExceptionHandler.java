package com.wimt.query.api;

import com.wimt.query.model.ServiceDegradedException;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler({ServiceDegradedException.class, CallNotPermittedException.class})
    public ResponseEntity<Map<String, Object>> degraded(Exception ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header("Retry-After", "10")
                .body(Map.of("degraded", true, "message", ex.getMessage() == null ? "read store unavailable" : ex.getMessage(),
                        "timestamp", Instant.now().toString()));
    }

    @ExceptionHandler(BulkheadFullException.class)
    public ResponseEntity<Map<String, Object>> overloaded(BulkheadFullException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "1")
                .body(Map.of("error", "BULKHEAD_FULL", "message", "Too many concurrent reads", "timestamp", Instant.now().toString()));
    }
}
