package io.github.hhagenbuch.meter.starter;

import io.github.hhagenbuch.agent.config.AgentProperties;
import io.github.hhagenbuch.agent.llm.LlmClient;
import io.github.hhagenbuch.agent.llm.LlmResponse;
import io.github.hhagenbuch.agent.llm.TokenUsage;
import io.github.hhagenbuch.meter.spring.autoconfigure.MeterAutoConfiguration;
import io.github.hhagenbuch.meter.starter.autoconfigure.MeterStarterAutoConfiguration;
import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MeterStarterAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MeterAutoConfiguration.class, MeterStarterAutoConfiguration.class))
            .withUserConfiguration(StarterBeans.class);

    @Test
    void theStarterLlmClientBeanIsWrappedWithMeteringWithZeroCodeChanges() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(LlmClient.class)).isInstanceOf(StarterMeteringLlmClient.class);
        });
    }

    @Test
    void meteringPostProcessorRunsOutermost() {
        runner.run(context -> {
            LlmClientMeteringBeanPostProcessor bpp = context.getBean(LlmClientMeteringBeanPostProcessor.class);
            // LOWEST_PRECEDENCE => applied last => wraps outermost (outside any recorder decorator).
            assertThat(bpp.getOrder()).isEqualTo(Ordered.LOWEST_PRECEDENCE);
        });
    }

    @Test
    void backsOffEntirelyWithoutAnOpenTelemetryBean() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(MeterAutoConfiguration.class, MeterStarterAutoConfiguration.class))
                .withUserConfiguration(StarterBeansNoOtel.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    // No OTel => no wrapping; the raw starter client stays.
                    assertThat(context.getBean(LlmClient.class)).isNotInstanceOf(StarterMeteringLlmClient.class);
                });
    }

    @Configuration
    static class StarterBeans {
        @Bean
        LlmClient fakeStarterLlmClient() {
            return (messages, tools) -> Mono.just(new LlmResponse("hi", List.of(), null, "end_turn", TokenUsage.EMPTY));
        }

        @Bean
        AgentProperties agentProperties() {
            return new AgentProperties("", "test-model", 1024, 6, 3, List.of());
        }

        @Bean
        OpenTelemetry openTelemetry() {
            return OpenTelemetry.noop();
        }
    }

    @Configuration
    static class StarterBeansNoOtel {
        @Bean
        LlmClient fakeStarterLlmClient() {
            return (messages, tools) -> Mono.just(new LlmResponse("hi", List.of(), null, "end_turn", TokenUsage.EMPTY));
        }

        @Bean
        AgentProperties agentProperties() {
            return new AgentProperties("", "test-model", 1024, 6, 3, List.of());
        }
    }
}
