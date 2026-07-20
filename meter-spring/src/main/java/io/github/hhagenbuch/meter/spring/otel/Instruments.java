package io.github.hhagenbuch.meter.spring.otel;

import io.github.hhagenbuch.meter.core.attr.MeterAttributes;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleCounter;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.Meter;

/**
 * The OTel metrics agent-meter records, built once from a {@link Meter}. The {@code add*}
 * methods are the sole recording facade so any decorator (the sync client, the reactive
 * starter adapter) records identically.
 *
 * <p><b>Standards alignment:</b> token usage and operation duration emit the ratified
 * OpenTelemetry GenAI metric names, units, and instrument types
 * ({@code gen_ai.client.token.usage} histogram in {@code {token}};
 * {@code gen_ai.client.operation.duration} histogram in {@code s}) as audited at semconv
 * commit {@code c26a2c21} (2026-07-17). Cost and the unknown-model counter have no
 * convention yet, so they keep the {@code agent.*} namespace rather than squat on
 * unratified {@code gen_ai.*} names. See the "Standards alignment" table in the README.
 */
public final class Instruments {

    private final LongHistogram tokenUsage;
    private final DoubleCounter cost;
    private final DoubleHistogram operationDuration;
    private final LongCounter unknownModel;

    public Instruments(Meter meter) {
        // Ratified gen_ai metrics — name/unit/instrument match the convention exactly.
        this.tokenUsage = meter.histogramBuilder("gen_ai.client.token.usage")
                .ofLongs()
                .setDescription("Number of input and output tokens used.").setUnit("{token}").build();
        this.operationDuration = meter.histogramBuilder("gen_ai.client.operation.duration")
                .setDescription("GenAI operation duration.").setUnit("s").build();
        // No ratified convention — keep the agent.* namespace (never squat on gen_ai.*).
        this.cost = meter.counterBuilder("agent.cost_usd").ofDoubles()
                .setDescription("Estimated LLM spend").setUnit("USD").build();
        this.unknownModel = meter.counterBuilder("agent.cost.unknown_model")
                .setDescription("Calls whose model had no price (cost recorded as unknown, not zero)")
                .build();
    }

    /** Records token count on {@code gen_ai.client.token.usage} with the required
     *  {@code gen_ai.token.type} attribute ({@code input}/{@code output}). */
    public void addTokens(long count, String tokenType, Attributes dims) {
        tokenUsage.record(count, dims.toBuilder().put(MeterAttributes.GEN_AI_TOKEN_TYPE, tokenType).build());
    }

    public void addCost(double usd, Attributes dims) {
        cost.add(usd, dims);
    }

    /** Records the operation duration on {@code gen_ai.client.operation.duration} in seconds. */
    public void recordDurationSeconds(double seconds, Attributes dims) {
        operationDuration.record(seconds, dims);
    }

    public void addUnknownModel(Attributes dims) {
        unknownModel.add(1, dims);
    }
}
