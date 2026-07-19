package io.github.hhagenbuch.meter.core.budget;

/**
 * The attribution context of a call, used to decide which budgets apply and which
 * spend bucket to charge. {@code feature} and {@code sessionId} may be null (untagged).
 */
public record CallContext(String feature, String sessionId, String model) {
}
