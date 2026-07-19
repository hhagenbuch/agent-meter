package io.github.hhagenbuch.meter.core.pricing;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * The versioned price table: a set of model pricings plus the date it was compiled,
 * so staleness can be detected loudly (DESIGN.md section 5).
 *
 * @param generatedOn when these prices were retrieved from public pricing pages
 * @param source      human note on provenance of the numbers
 */
public record PriceTable(LocalDate generatedOn, String source, List<ModelPricing> models) {

    public PriceTable {
        models = models == null ? List.of() : models;
    }

    public Optional<ModelPricing> pricing(String model) {
        return models.stream().filter(m -> m.model().equals(model)).findFirst();
    }

    /** True when the table is older than {@code maxAgeDays} at {@code now} (or has no date). */
    public boolean isStale(LocalDate now, int maxAgeDays) {
        return generatedOn == null || generatedOn.plusDays(maxAgeDays).isBefore(now);
    }
}
