package com.wimt.gateway;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** Degraded responses returned when a downstream circuit breaker is open / times out. */
@RestController
public class FallbackController {

    private final ReactiveStringRedisTemplate redis;

    public FallbackController(ReactiveStringRedisTemplate redis) {
        this.redis = redis;
    }

    @RequestMapping("/fallback/query")
    public Mono<ResponseEntity<String>> queryFallback(ServerWebExchange exchange) {
        Set<URI> originals = exchange.getAttributeOrDefault(
                ServerWebExchangeUtils.GATEWAY_ORIGINAL_REQUEST_URL_ATTR, new LinkedHashSet<>());
        if (originals.isEmpty()) {
            return Mono.just(unavailable("train-query-service"));
        }
        String key = ResponseCacheFilter.cacheKey(originals.iterator().next());
        return redis.opsForValue().get(key)
                .map(body -> ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Degraded", "cached-response")
                        .header("Warning", "110 - \"Response is stale\"")
                        .body(body))
                .onErrorResume(e -> Mono.empty())
                .switchIfEmpty(Mono.fromSupplier(() -> unavailable("train-query-service")));
    }

    @RequestMapping("/fallback/ingest")
    public Mono<ResponseEntity<Map<String, Object>>> ingestFallback() {
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Retry-After", "10")
                .header("X-Degraded", "ingestion-unavailable")
                .body(degraded("telemetry-ingestion-service",
                        "Telemetry ingestion is temporarily unavailable. Retry shortly; devices should buffer pings locally.")));
    }

    @RequestMapping("/fallback/saga")
    public Mono<ResponseEntity<Map<String, Object>>> sagaFallback() {
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Retry-After", "10")
                .body(degraded("train-saga-orchestrator", "Saga orchestrator is temporarily unavailable.")));
    }

    @RequestMapping("/fallback/eta")
    public Mono<ResponseEntity<Map<String, Object>>> etaFallback() {
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Retry-After", "10")
                .body(degraded("eta-engine-service", "ETA engine is temporarily unavailable.")));
    }

    private ResponseEntity<String> unavailable(String service) {
        String json = "{\"degraded\":true,\"service\":\"" + service
                + "\",\"message\":\"Live data is temporarily unavailable and no cached copy exists.\",\"timestamp\":\""
                + Instant.now() + "\"}";
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Retry-After", "10")
                .header("X-Degraded", "no-cache")
                .body(json);
    }

    private Map<String, Object> degraded(String service, String message) {
        return Map.of("degraded", true, "service", service, "message", message, "timestamp", Instant.now().toString());
    }
}
