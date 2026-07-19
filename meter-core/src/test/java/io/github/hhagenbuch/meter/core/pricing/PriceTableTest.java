package io.github.hhagenbuch.meter.core.pricing;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class PriceTableTest {

    @Test
    void bundledTableLoadsWithKnownModels() {
        PriceTable table = PriceTableLoader.bundled();

        assertThat(table.generatedOn()).isNotNull();
        assertThat(table.models()).extracting(ModelPricing::model)
                .contains("claude-opus-4-8", "claude-sonnet-5", "claude-haiku-4-5");

        RatePeriod sonnet = table.pricing("claude-sonnet-5").orElseThrow()
                .rateAt(LocalDate.of(2026, 7, 19)).orElseThrow();
        assertThat(sonnet.inputPerMTok()).isEqualTo(3.00);
        assertThat(sonnet.outputPerMTok()).isEqualTo(15.00);
        assertThat(sonnet.cachedInputPerMTok()).isEqualTo(0.30);
    }

    @Test
    void stalenessComparesAgainstGeneratedOn() {
        PriceTable table = new PriceTable(LocalDate.of(2026, 7, 1), "t", java.util.List.of());
        assertThat(table.isStale(LocalDate.of(2026, 7, 1), 90)).isFalse();
        assertThat(table.isStale(LocalDate.of(2026, 9, 28), 90)).isFalse();  // exactly 89 days
        assertThat(table.isStale(LocalDate.of(2026, 10, 1), 90)).isTrue();   // past 90 days
    }

    @Test
    void missingGeneratedOnIsTreatedAsStale() {
        PriceTable table = new PriceTable(null, "t", java.util.List.of());
        assertThat(table.isStale(LocalDate.of(2026, 1, 1), 90)).isTrue();
    }
}
