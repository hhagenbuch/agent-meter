package io.github.hhagenbuch.meter.core.cost;

import io.github.hhagenbuch.meter.core.pricing.ModelPricing;
import io.github.hhagenbuch.meter.core.pricing.PriceTable;
import io.github.hhagenbuch.meter.core.pricing.RatePeriod;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class CostEngineTest {

    private static final LocalDate JAN = LocalDate.of(2026, 1, 1);
    private static final LocalDate JUN = LocalDate.of(2026, 6, 1);

    // "m1" rises in price on Jun 1; table compiled Jul 1.
    private final PriceTable table = new PriceTable(LocalDate.of(2026, 7, 1), "test", List.of(
            new ModelPricing("m1", List.of(
                    new RatePeriod(JAN, 10.0, 30.0, 1.0),
                    new RatePeriod(JUN, 12.0, 36.0, 1.2)))));
    private final CostEngine engine = new CostEngine(table);

    @Test
    void picksTheRateEffectiveAtTheCallDate() {
        TokenUsage oneEach = TokenUsage.of(1_000_000, 1_000_000);
        // day before the price rise -> old rate (10 + 30)
        assertThat(engine.cost("m1", oneEach, LocalDate.of(2026, 5, 31)).usd()).isCloseTo(40.0, within(1e-9));
        // on the effective date -> new rate (12 + 36)
        assertThat(engine.cost("m1", oneEach, JUN).usd()).isCloseTo(48.0, within(1e-9));
        // after -> new rate
        assertThat(engine.cost("m1", oneEach, LocalDate.of(2026, 6, 2)).usd()).isCloseTo(48.0, within(1e-9));
    }

    @Test
    void cachedInputTokensBillAtTheCachedRate() {
        // 1M input @10 + 1M cached @1.0 = 11.0
        CostResult r = engine.cost("m1", new TokenUsage(1_000_000, 0, 1_000_000), JAN);
        assertThat(r.known()).isTrue();
        assertThat(r.usd()).isCloseTo(11.0, within(1e-9));
    }

    @Test
    void unknownModelIsUnknownNotZero() {
        CostResult r = engine.cost("no-such-model", TokenUsage.of(1_000_000, 1_000_000), JAN);
        assertThat(r.known()).isFalse();
        assertThat(engine.knows("no-such-model")).isFalse();
        assertThat(engine.knows("m1")).isTrue();
    }

    @Test
    void beforeAnyEffectiveDateThereIsNoRate() {
        CostResult r = engine.cost("m1", TokenUsage.of(1_000_000, 1_000_000), LocalDate.of(2025, 12, 31));
        assertThat(r.known()).isFalse();
    }

    @Test
    void zeroUsageIsAKnownZeroCost() {
        CostResult r = engine.cost("m1", TokenUsage.NONE, JAN);
        assertThat(r.known()).isTrue();
        assertThat(r.usd()).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void staleTableFlagsCostAsEstimated() {
        assertThat(engine.cost("m1", TokenUsage.of(1, 1), LocalDate.of(2026, 7, 1)).estimated()).isFalse();
        // > 90 days after Jul 1 compile date
        CostResult stale = engine.cost("m1", TokenUsage.of(1_000_000, 0), LocalDate.of(2026, 12, 1));
        assertThat(stale.known()).isTrue();
        assertThat(stale.estimated()).isTrue();
    }
}
