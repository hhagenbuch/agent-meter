package io.github.hhagenbuch.meter.core.cost;

/**
 * Token counts from an API response's {@code usage}. Cached-input tokens are billed
 * at a separate (lower) rate when the model reports them.
 */
public record TokenUsage(long inputTokens, long outputTokens, long cachedInputTokens) {

    public static TokenUsage of(long inputTokens, long outputTokens) {
        return new TokenUsage(inputTokens, outputTokens, 0);
    }

    public static final TokenUsage NONE = new TokenUsage(0, 0, 0);
}
