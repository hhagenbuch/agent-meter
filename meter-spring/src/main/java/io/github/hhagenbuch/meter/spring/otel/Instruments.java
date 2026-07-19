package io.github.hhagenbuch.meter.spring.otel;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleCounter;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;

/**
 * The OTel metrics agent-meter records, built once from a {@link Meter}. The {@code add*}
 * methods are the public recording facade so any decorator (the sync client, the reactive
 * starter adapter) records identically.
 */
public final class Instruments {

    static final AttributeKey<String> DIRECTION = AttributeKey.stringKey("direction");

    final LongCounter tokens;
    final DoubleCounter cost;
    final DoubleHistogram turnDuration;
    final LongCounter unknownModel;

    public Instruments(Meter meter) {
        this.tokens = meter.counterBuilder("agent.tokens")
                .setDescription("LLM tokens consumed").setUnit("{token}").build();
        this.cost = meter.counterBuilder("agent.cost_usd").ofDoubles()
                .setDescription("Estimated LLM spend").setUnit("USD").build();
        this.turnDuration = meter.histogramBuilder("agent.turn.duration")
                .setDescription("Wall time of an agent turn").setUnit("ms").build();
        this.unknownModel = meter.counterBuilder("agent.cost.unknown_model")
                .setDescription("Calls whose model had no price (cost recorded as unknown, not zero)")
                .build();
    }

    public void addTokens(long count, String direction, Attributes dims) {
        tokens.add(count, dims.toBuilder().put(DIRECTION, direction).build());
    }

    public void addCost(double usd, Attributes dims) {
        cost.add(usd, dims);
    }

    public void recordDuration(double millis, Attributes dims) {
        turnDuration.record(millis, dims);
    }

    public void addUnknownModel(Attributes dims) {
        unknownModel.add(1, dims);
    }
}
