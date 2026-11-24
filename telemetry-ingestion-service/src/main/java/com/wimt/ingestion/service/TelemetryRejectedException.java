package com.wimt.ingestion.service;

/** Command-side validation failure (domain invariant violated). */
public class TelemetryRejectedException extends RuntimeException {
    private final String code;

    public TelemetryRejectedException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
