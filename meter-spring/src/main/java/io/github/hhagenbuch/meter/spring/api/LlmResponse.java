package io.github.hhagenbuch.meter.spring.api;

import io.github.hhagenbuch.meter.core.cost.TokenUsage;

import java.util.List;

/**
 * What a metered call produced. {@code attempts} is the retry breakdown; {@code incomplete}
 * marks a stream that died before its terminal usage event — partial cost is real cost.
 */
public record LlmResponse(String responseModel, List<Attempt> attempts, boolean incomplete) {

    public LlmResponse {
        attempts = attempts == null ? List.of() : List.copyOf(attempts);
    }

    /** A single successful attempt — the common (no-retry) case. */
    public static LlmResponse single(String responseModel, TokenUsage usage) {
        return new LlmResponse(responseModel, List.of(Attempt.ok(usage, 0)), false);
    }

    /** Total usage across every attempt — you pay for retries. */
    public TokenUsage totalUsage() {
        long in = 0;
        long out = 0;
        long cached = 0;
        for (Attempt a : attempts) {
            in += a.usage().inputTokens();
            out += a.usage().outputTokens();
            cached += a.usage().cachedInputTokens();
        }
        return new TokenUsage(in, out, cached);
    }
}
