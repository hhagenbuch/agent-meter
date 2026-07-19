package io.github.hhagenbuch.meter.starter.autoconfigure;

import io.github.hhagenbuch.agent.config.AgentProperties;
import io.github.hhagenbuch.agent.llm.LlmClient;
import io.github.hhagenbuch.meter.core.budget.BudgetLedger;
import io.github.hhagenbuch.meter.core.cost.CostEngine;
import io.github.hhagenbuch.meter.spring.autoconfigure.MeterAutoConfiguration;
import io.github.hhagenbuch.meter.spring.autoconfigure.MeterProperties;
import io.github.hhagenbuch.meter.spring.budget.BudgetEventListener;
import io.github.hhagenbuch.meter.spring.otel.Instruments;
import io.github.hhagenbuch.meter.starter.LlmClientMeteringBeanPostProcessor;
import io.github.hhagenbuch.meter.starter.MeterContextWebFilter;
import io.github.hhagenbuch.meter.starter.StarterMeteringFactory;
import io.opentelemetry.api.OpenTelemetry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

/**
 * Auto-instruments a {@code spring-ai-agent-starter} application: with this module on the
 * classpath and an {@link OpenTelemetry} bean present, the starter's {@link LlmClient} is
 * wrapped with metering + budget enforcement — no code changes in the app. Runs after the
 * core meter auto-config, whose {@code CostEngine}/{@code Instruments}/{@code BudgetLedger}
 * beans it reuses.
 */
@AutoConfiguration(after = MeterAutoConfiguration.class)
@ConditionalOnClass({OpenTelemetry.class, LlmClient.class})
@ConditionalOnBean(OpenTelemetry.class)
public class MeterStarterAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public StarterMeteringFactory starterMeteringFactory(OpenTelemetry openTelemetry, Instruments instruments,
                                                         CostEngine costEngine, BudgetLedger ledger,
                                                         BudgetEventListener budgetListener,
                                                         AgentProperties agentProperties, MeterProperties meterProps) {
        return new StarterMeteringFactory(openTelemetry.getTracer("agent-meter"), instruments, costEngine,
                ledger, budgetListener, agentProperties, Clock.systemUTC(),
                meterProps.getGenAiSystem(), meterProps.getPromptVersion());
    }

    /** Static so the post-processor is created early without prematurely initializing the meter beans. */
    @Bean
    public static LlmClientMeteringBeanPostProcessor llmClientMeteringBeanPostProcessor(
            ObjectProvider<StarterMeteringFactory> factory) {
        return new LlmClientMeteringBeanPostProcessor(factory);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    public MeterContextWebFilter meterContextWebFilter() {
        return new MeterContextWebFilter();
    }
}
