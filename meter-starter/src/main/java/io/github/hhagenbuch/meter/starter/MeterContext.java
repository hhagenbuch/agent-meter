package io.github.hhagenbuch.meter.starter;

/**
 * Reactor-context keys the adapter reads for attribution. They can't come from the
 * starter's {@code LlmClient.chat(messages, tools)} — it has no session/feature argument —
 * so {@link MeterContextWebFilter} lifts them from request headers into the reactive
 * context, where the decorator picks them up via {@code Mono.deferContextual}.
 */
public final class MeterContext {

    /** Session id (from the {@code X-Session-Id} request header). */
    public static final String SESSION_ID = "agent.session_id";

    /** Feature tag / FinOps unit (from the {@code X-Feature} request header). */
    public static final String FEATURE = "agent.feature";

    private MeterContext() {
    }
}
