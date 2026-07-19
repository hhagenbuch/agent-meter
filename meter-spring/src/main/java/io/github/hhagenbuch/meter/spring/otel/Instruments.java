package io.github.hhagenbuch.meter.spring.otel;

import io.opentelemetry.api.metrics.DoubleCounter;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;

/** The OTel metrics agent-meter records, built once from a {@link Meter}. */
public final class Instruments {

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
}
