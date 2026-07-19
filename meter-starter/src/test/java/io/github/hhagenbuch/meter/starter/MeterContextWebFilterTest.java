package io.github.hhagenbuch.meter.starter;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class MeterContextWebFilterTest {

    private final MeterContextWebFilter filter = new MeterContextWebFilter();

    @Test
    void liftsSessionAndFeatureHeadersIntoTheReactorContext() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/chat")
                .header(MeterContextWebFilter.SESSION_HEADER, "s1")
                .header(MeterContextWebFilter.FEATURE_HEADER, "support-chat"));

        AtomicReference<String> session = new AtomicReference<>();
        AtomicReference<String> feature = new AtomicReference<>();
        WebFilterChain chain = ex -> Mono.deferContextual(ctx -> {
            session.set(ctx.getOrDefault(MeterContext.SESSION_ID, null));
            feature.set(ctx.getOrDefault(MeterContext.FEATURE, null));
            return Mono.empty();
        });

        filter.filter(exchange, chain).block();

        assertThat(session.get()).isEqualTo("s1");
        assertThat(feature.get()).isEqualTo("support-chat");
    }

    @Test
    void noHeadersLeavesTheContextUntouched() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/chat"));
        AtomicReference<String> session = new AtomicReference<>("unset");
        WebFilterChain chain = ex -> Mono.deferContextual(ctx -> {
            session.set(ctx.getOrDefault(MeterContext.SESSION_ID, null));
            return Mono.empty();
        });

        filter.filter(exchange, chain).block();

        assertThat(session.get()).isNull();
    }
}
