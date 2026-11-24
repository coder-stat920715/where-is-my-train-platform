package com.wimt.ingestion.api;

import java.util.UUID;

public record TelemetryResponse(Long pingId, String status, UUID eventId) {
}
