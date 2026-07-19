package io.github.hhagenbuch.meter.spring.autoconfigure;

import io.github.hhagenbuch.meter.core.cost.CostEngine;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Bound from the {@code meter.*} namespace. */
@ConfigurationProperties(prefix = "meter")
public class MeterProperties {

    /** Override for the bundled price table; when null, the jar's {@code prices.yaml} is used. */
    private String priceTablePath;

    /** A table older than this many days flags cost as estimated. */
    private int staleDays = CostEngine.DEFAULT_STALE_DAYS;

    /** {@code gen_ai.system} value for spans (e.g. anthropic, openai). */
    private String genAiSystem = "anthropic";

    public String getPriceTablePath() {
        return priceTablePath;
    }

    public void setPriceTablePath(String priceTablePath) {
        this.priceTablePath = priceTablePath;
    }

    public int getStaleDays() {
        return staleDays;
    }

    public void setStaleDays(int staleDays) {
        this.staleDays = staleDays;
    }

    public String getGenAiSystem() {
        return genAiSystem;
    }

    public void setGenAiSystem(String genAiSystem) {
        this.genAiSystem = genAiSystem;
    }
}
