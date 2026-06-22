package com.banking.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Component
public class LoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(LoggingFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        long startTime = Instant.now().toEpochMilli();

        // Injecter le traceId dans la requête
        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(r -> r.header("X-Trace-Id", traceId))
                .build();

        log.info("[{}] → {} {} (from: {})",
                traceId,
                request.getMethod(),
                request.getURI().getPath(),
                request.getRemoteAddress());

        return chain.filter(mutatedExchange)
                .doFinally(signal -> {
                    long duration = Instant.now().toEpochMilli() - startTime;
                    int statusCode = exchange.getResponse().getStatusCode() != null
                            ? exchange.getResponse().getStatusCode().value() : 0;
                    log.info("[{}] ← {} {} {}ms",
                            traceId,
                            statusCode,
                            request.getURI().getPath(),
                            duration);
                });
    }

    @Override
    public int getOrder() {
        return -1; // Priorité la plus haute = s'exécute en premier
    }
}