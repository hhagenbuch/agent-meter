package io.github.hhagenbuch.meter.spring.api;

/**
 * The attribution context of a call, plus the model requested. The message payload is
 * the caller's concern; metering only needs the model and the FinOps dimensions.
 * {@code tool} / {@code promptVersion} / {@code feature} / {@code sessionId} may be null.
 */
public record LlmRequest(String requestedModel, String feature, String sessionId,
                         String tool, String promptVersion) {

    public static LlmRequest of(String model) {
        return new LlmRequest(model, null, null, null, null);
    }
}
