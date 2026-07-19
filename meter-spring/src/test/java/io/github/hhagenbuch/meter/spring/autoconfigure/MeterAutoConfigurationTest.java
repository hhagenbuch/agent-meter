package io.github.hhagenbuch.meter.spring.autoconfigure;

import io.github.hhagenbuch.meter.core.cost.CostEngine;
import io.github.hhagenbuch.meter.core.cost.TokenUsage;
import io.github.hhagenbuch.meter.spring.api.LlmResponse;
import io.github.hhagenbuch.meter.spring.otel.Instruments;
import io.github.hhagenbuch.meter.spring.otel.MeteringFactory;
import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the auto-config's bean factory methods directly — no Spring context needed,
 * which keeps the test fast and independent of the runtime Boot/JDK combination.
 */
class MeterAutoConfigurationTest {

    private final MeterAutoConfiguration config = new MeterAutoConfiguration();
    private final MeterProperties props = new MeterProperties();

    @Test
    void costEngineBeanLoadsTheBundledPriceTable() {
        CostEngine engine = config.agentMeterCostEngine(props);
        assertThat(engine.knows("claude-sonnet-5")).isTrue();
        assertThat(engine.knows("no-such-model")).isFalse();
    }

    @Test
    void factoryWrapsADelegateIntoAMeteringClient() {
        OpenTelemetry otel = OpenTelemetry.noop();
        CostEngine engine = config.agentMeterCostEngine(props);
        Instruments instruments = config.agentMeterInstruments(otel);
        MeteringFactory factory = config.agentMeteringFactory(otel, instruments, engine, props);

        var metered = factory.wrap(request -> LlmResponse.single("claude-sonnet-5", TokenUsage.of(10, 5)));
        // With a no-op OTel it records nothing, but the call must pass through cleanly.
        assertThat(metered.call(io.github.hhagenbuch.meter.spring.api.LlmRequest.of("claude-sonnet-5")).responseModel())
                .isEqualTo("claude-sonnet-5");
    }

    @Test
    void defaultsAreSensible() {
        assertThat(props.getGenAiSystem()).isEqualTo("anthropic");
        assertThat(props.getStaleDays()).isEqualTo(CostEngine.DEFAULT_STALE_DAYS);
        assertThat(props.getPriceTablePath()).isNull();
    }
}
