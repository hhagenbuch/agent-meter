package io.github.hhagenbuch.meter.spring.autoconfigure;

import io.github.hhagenbuch.meter.core.budget.BudgetLedger;
import io.github.hhagenbuch.meter.core.budget.BudgetStore;
import io.github.hhagenbuch.meter.core.budget.InMemoryBudgetStore;
import io.github.hhagenbuch.meter.core.cost.CostEngine;
import io.github.hhagenbuch.meter.core.pricing.PriceTable;
import io.github.hhagenbuch.meter.core.pricing.PriceTableLoader;
import io.github.hhagenbuch.meter.spring.Instrumentation;
import io.github.hhagenbuch.meter.spring.budget.BudgetEventListener;
import io.github.hhagenbuch.meter.spring.otel.Instruments;
import io.github.hhagenbuch.meter.spring.otel.MeteringFactory;
import io.github.hhagenbuch.meter.spring.otel.MetricBudgetEventListener;
import io.opentelemetry.api.OpenTelemetry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.nio.file.Path;
import java.time.Clock;

/**
 * Wires the cost engine, OTel instruments, and the metering factory. Activates when an
 * {@link OpenTelemetry} bean is present; each bean backs off if the application defines
 * its own, so everything is overridable.
 */
@AutoConfiguration
@EnableConfigurationProperties(MeterProperties.class)
@ConditionalOnBean(OpenTelemetry.class)
public class MeterAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CostEngine agentMeterCostEngine(MeterProperties props) {
        PriceTable table = props.getPriceTablePath() == null
                ? PriceTableLoader.bundled()
                : PriceTableLoader.fromPath(Path.of(props.getPriceTablePath()));
        return new CostEngine(table, props.getStaleDays());
    }

    @Bean
    @ConditionalOnMissingBean
    public Instruments agentMeterInstruments(OpenTelemetry openTelemetry) {
        return new Instruments(openTelemetry.getMeter("agent-meter"));
    }

    @Bean
    @ConditionalOnMissingBean
    public MeteringFactory agentMeteringFactory(OpenTelemetry openTelemetry, Instruments instruments,
                                                CostEngine costEngine, MeterProperties props) {
        return new MeteringFactory(openTelemetry.getTracer("agent-meter"), instruments,
                costEngine, Clock.systemUTC(), props.getGenAiSystem());
    }

    @Bean
    @ConditionalOnMissingBean
    public BudgetStore agentMeterBudgetStore() {
        return new InMemoryBudgetStore();
    }

    @Bean
    @ConditionalOnMissingBean
    public BudgetLedger agentMeterBudgetLedger(MeterProperties props, BudgetStore store) {
        return new BudgetLedger(props.toBudgets(), store, Clock.systemUTC());
    }

    @Bean
    @ConditionalOnMissingBean
    public BudgetEventListener agentMeterBudgetEventListener(OpenTelemetry openTelemetry) {
        return new MetricBudgetEventListener(openTelemetry.getMeter("agent-meter"));
    }

    /** The one-call entry point: {@code instrumentation.instrument(providerClient)}. */
    @Bean
    @ConditionalOnMissingBean
    public Instrumentation agentMeterInstrumentation(MeteringFactory metering, BudgetLedger ledger,
                                                     CostEngine costEngine, BudgetEventListener listener) {
        return new Instrumentation(metering, ledger, costEngine, Clock.systemUTC(), listener);
    }
}
