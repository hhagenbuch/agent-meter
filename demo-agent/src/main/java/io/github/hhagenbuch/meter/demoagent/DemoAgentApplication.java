package io.github.hhagenbuch.meter.demoagent;

import io.github.hhagenbuch.agent.AgentStarterApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * The REAL agent, instrumented by adding two dependencies (spring-ai-agent-starter +
 * meter-starter) and zero code. Component-scans the starter's package to load its beans
 * (AgentLoop, AnthropicClient, ChatController, ...); meter-starter's auto-config then
 * wraps the {@code LlmClient}. With {@code ANTHROPIC_API_KEY} set, every chat call emits
 * real token/cost telemetry. The starter's own {@code @SpringBootApplication} is excluded
 * from the scan so there's exactly one application class.
 */
@SpringBootApplication(scanBasePackages = {
        "io.github.hhagenbuch.agent",
        "io.github.hhagenbuch.meter.demoagent"
})
@ConfigurationPropertiesScan(basePackages = "io.github.hhagenbuch.agent")
@ComponentScan(basePackages = {"io.github.hhagenbuch.agent", "io.github.hhagenbuch.meter.demoagent"},
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = AgentStarterApplication.class))
public class DemoAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoAgentApplication.class, args);
    }
}
