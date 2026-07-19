package io.github.hhagenbuch.meter.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * A tiny app that drives agent-meter with a fake LLM provider and exports OTLP to the
 * demo stack, so the Grafana dashboard has real data. Not a product — a load target.
 */
@SpringBootApplication
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
