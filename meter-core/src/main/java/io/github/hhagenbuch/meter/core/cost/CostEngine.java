package io.github.hhagenbuch.meter.core.cost;

import io.github.hhagenbuch.meter.core.pricing.ModelPricing;
import io.github.hhagenbuch.meter.core.pricing.PriceTable;
import io.github.hhagenbuch.meter.core.pricing.RatePeriod;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Pure cost computation over a {@link PriceTable}. Selects the rate effective at the
 * call date, sums input/output (and cached-input) token cost, and flags the result as
 * estimated when the table is stale. An unknown model yields {@link CostResult#UNKNOWN},
 * never a zero cost.
 */
public final class CostEngine {

    /** A table older than this many days is stale (DESIGN.md section 5). */
    public static final int DEFAULT_STALE_DAYS = 90;

    private static final double PER_MTOK = 1_000_000.0;

    private final PriceTable table;
    private final int staleDays;

    public CostEngine(PriceTable table) {
        this(table, DEFAULT_STALE_DAYS);
    }

    public CostEngine(PriceTable table, int staleDays) {
        this.table = table;
        this.staleDays = staleDays;
    }

    public boolean isStale(LocalDate asOf) {
        return table.isStale(asOf, staleDays);
    }

    /** Cost of one call. {@code at} is the call date, used for rate selection and staleness. */
    public CostResult cost(String model, TokenUsage usage, LocalDate at) {
        Optional<RatePeriod> rate = table.pricing(model).flatMap(mp -> mp.rateAt(at));
        if (rate.isEmpty()) {
            return CostResult.UNKNOWN; // unknown model, or no rate effective yet at this date
        }
        RatePeriod r = rate.get();
        double usd = usage.inputTokens() / PER_MTOK * r.inputPerMTok()
                + usage.outputTokens() / PER_MTOK * r.outputPerMTok();
        if (usage.cachedInputTokens() > 0 && r.cachedInputPerMTok() != null) {
            usd += usage.cachedInputTokens() / PER_MTOK * r.cachedInputPerMTok();
        }
        return CostResult.of(usd, isStale(at));
    }

    public boolean knows(String model) {
        return table.pricing(model).map(ModelPricing::rates).map(rs -> !rs.isEmpty()).orElse(false);
    }
}
