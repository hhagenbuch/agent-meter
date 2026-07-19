package io.github.hhagenbuch.meter.spring.otel;

import io.github.hhagenbuch.meter.core.cost.CostEngine;
import io.github.hhagenbuch.meter.spring.api.LlmClient;
import io.opentelemetry.api.trace.Tracer;

import java.time.Clock;

/**
 * Wraps any {@link LlmClient} with metering. Injected as a bean so an application can
 * decorate its provider client: {@code meteringFactory.wrap(anthropicClient)}.
 */
public final class MeteringFactory {

    private final Tracer tracer;
    private final Instruments instruments;
    private final CostEngine costEngine;
    private final Clock clock;
    private final String genAiSystem;

    public MeteringFactory(Tracer tracer, Instruments instruments, CostEngine costEngine,
                           Clock clock, String genAiSystem) {
        this.tracer = tracer;
        this.instruments = instruments;
        this.costEngine = costEngine;
        this.clock = clock;
        this.genAiSystem = genAiSystem;
    }

    public MeteringLlmClient wrap(LlmClient delegate) {
        return new MeteringLlmClient(delegate, tracer, instruments, costEngine, clock, genAiSystem);
    }
}
