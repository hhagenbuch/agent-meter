package io.github.hhagenbuch.meter.spring.autoconfigure;

import io.github.hhagenbuch.meter.core.budget.Budget;
import io.github.hhagenbuch.meter.core.budget.BudgetAction;
import io.github.hhagenbuch.meter.core.cost.CostEngine;
import io.github.hhagenbuch.meter.core.cost.TokenUsage;
import io.github.hhagenbuch.meter.spring.api.LlmResponse;
import io.github.hhagenbuch.meter.spring.otel.Instruments;
import io.github.hhagenbuch.meter.spring.otel.MeteringFactory;
import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Test;

import java.util.List;

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
    void budgetsParseFromDeclarativeConfig() {
        MeterProperties.BudgetSpec spec = new MeterProperties.BudgetSpec();
        spec.setScope("feature:support-chat");
        spec.setLimitUsd(5.0);
        spec.setWindow("daily");
        spec.setOnBreach("degrade");
        spec.setDegradeTo("claude-haiku-4-5");
        props.setBudgets(List.of(spec));

        List<Budget> budgets = props.toBudgets();
        assertThat(budgets).hasSize(1);
        assertThat(budgets.get(0).onBreach()).isEqualTo(BudgetAction.DEGRADE);
        assertThat(budgets.get(0).degradeTo()).isEqualTo("claude-haiku-4-5");
        assertThat(budgets.get(0).limitUsd()).isEqualTo(5.0);
    }

    @Test
    void instrumentationBeanWiresTheFullStack() {
        OpenTelemetry otel = OpenTelemetry.noop();
        CostEngine engine = config.agentMeterCostEngine(props);
        Instruments instruments = config.agentMeterInstruments(otel);
        MeteringFactory metering = config.agentMeteringFactory(otel, instruments, engine, props);
        var store = config.agentMeterBudgetStore();
        var ledger = config.agentMeterBudgetLedger(props, store);
        var listener = config.agentMeterBudgetEventListener(otel);
        var instrumentation = config.agentMeterInstrumentation(metering, ledger, engine, listener);

        var stack = instrumentation.instrument(r -> LlmResponse.single("claude-sonnet-5", TokenUsage.of(1, 1)));
        assertThat(stack.call(io.github.hhagenbuch.meter.spring.api.LlmRequest.of("claude-sonnet-5")).responseModel())
                .isEqualTo("claude-sonnet-5");
    }

    @Test
    void defaultsAreSensible() {
        assertThat(props.getGenAiSystem()).isEqualTo("anthropic");
        assertThat(props.getStaleDays()).isEqualTo(CostEngine.DEFAULT_STALE_DAYS);
        assertThat(props.getPriceTablePath()).isNull();
    }
}
