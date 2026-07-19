package io.github.hhagenbuch.meter.starter;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * Lifts {@code X-Session-Id} / {@code X-Feature} request headers into the Reactor context
 * so the metering decorator can attribute a span to a session and feature. Absent headers
 * simply mean those attributes aren't set (model, usage, and cost always are).
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MeterContextWebFilter implements WebFilter {

    public static final String SESSION_HEADER = "X-Session-Id";
    public static final String FEATURE_HEADER = "X-Feature";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        HttpHeaders headers = exchange.getRequest().getHeaders();
        String session = headers.getFirst(SESSION_HEADER);
        String feature = headers.getFirst(FEATURE_HEADER);
        if (session == null && feature == null) {
            return chain.filter(exchange);
        }
        // contextWrite makes these visible to everything downstream in the request handling,
        // including the LlmClient.chat call deep in the agent loop.
        return chain.filter(exchange).contextWrite(ctx -> {
            Context out = ctx;
            if (session != null) {
                out = out.put(MeterContext.SESSION_ID, session);
            }
            if (feature != null) {
                out = out.put(MeterContext.FEATURE, feature);
            }
            return out;
        });
    }
}
