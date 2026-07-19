package io.github.hhagenbuch.meter.spring.api;

/**
 * The attribution context of a call, plus the model requested. The message payload is
 * the caller's concern; metering only needs the model and the FinOps dimensions.
 * {@code tool} / {@code promptVersion} / {@code feature} / {@code sessionId} may be null.
 *
 * @param budgetDegraded set by budget enforcement when it swapped {@code requestedModel}
 *                       for a cheaper one; the meter tags the span {@code agent.budget_degraded}
 */
public record LlmRequest(String requestedModel, String feature, String sessionId,
                         String tool, String promptVersion, boolean budgetDegraded) {

    public LlmRequest(String requestedModel, String feature, String sessionId, String tool, String promptVersion) {
        this(requestedModel, feature, sessionId, tool, promptVersion, false);
    }

    public static LlmRequest of(String model) {
        return new LlmRequest(model, null, null, null, null, false);
    }

    /** A copy routed to a cheaper model by a budget, flagged so the meter records it. */
    public LlmRequest degradedTo(String cheaperModel) {
        return new LlmRequest(cheaperModel, feature, sessionId, tool, promptVersion, true);
    }
}
