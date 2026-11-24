package com.wimt.gateway;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Stores every successful GET /api/v1/query/** response in Redis so that the circuit-breaker
 * fallback can serve a cached (degraded) answer when the query service is unavailable.
 */
@Component
public class ResponseCacheFilter implements GlobalFilter, Ordered {

    public static final String CACHE_PREFIX = "gw:cache:";

    private final ReactiveStringRedisTemplate redis;
    private final Duration ttl;

    public ResponseCacheFilter(ReactiveStringRedisTemplate redis,
                               @Value("${gateway.fallback-cache-ttl-seconds:600}") long ttlSeconds) {
        this.redis = redis;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    public static String cacheKey(java.net.URI uri) {
        String q = uri.getRawQuery();
        return CACHE_PREFIX + uri.getRawPath() + (q == null ? "" : "?" + q);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        if (request.getMethod() != HttpMethod.GET || !request.getURI().getRawPath().startsWith("/api/v1/query")) {
            return chain.filter(exchange);
        }
        String key = cacheKey(request.getURI());
        ServerHttpResponse original = exchange.getResponse();
        DataBufferFactory factory = original.bufferFactory();

        ServerHttpResponseDecorator decorated = new ServerHttpResponseDecorator(original) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                if (getStatusCode() != null && getStatusCode().is2xxSuccessful() && body instanceof Flux<? extends DataBuffer> flux) {
                    Mono<DataBuffer> cached = DataBufferUtils.join(flux).flatMap(buffer -> {
                        byte[] bytes = new byte[buffer.readableByteCount()];
                        buffer.read(bytes);
                        DataBufferUtils.release(buffer);
                        return redis.opsForValue()
                                .set(key, new String(bytes, StandardCharsets.UTF_8), ttl)
                                .onErrorReturn(false)
                                .thenReturn(factory.wrap(bytes));
                    });
                    return super.writeWith(cached.flux());
                }
                return super.writeWith(body);
            }
        };
        return chain.filter(exchange.mutate().response(decorated).build());
    }

    @Override
    public int getOrder() {
        return -2;
    }
}
