package com.wimt.ingestion.api;

import com.wimt.ingestion.service.TelemetryRejectedException;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(TelemetryRejectedException.class)
    public ResponseEntity<Map<String, Object>> rejected(TelemetryRejectedException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of("error", ex.getCode(), "message", ex.getMessage(), "timestamp", Instant.now().toString()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> invalid(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + " " + e.getDefaultMessage()).reduce((a, b) -> a + "; " + b).orElse("invalid request");
        return ResponseEntity.badRequest().body(Map.of("error", "VALIDATION_FAILED", "message", msg));
    }
}
