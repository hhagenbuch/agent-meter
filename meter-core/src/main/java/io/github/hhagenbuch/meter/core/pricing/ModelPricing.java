package io.github.hhagenbuch.meter.core.pricing;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** All rate periods for one model. */
public record ModelPricing(String model, List<RatePeriod> rates) {

    public ModelPricing {
        rates = rates == null ? List.of() : rates;
    }

    /** The rate effective at {@code date}: the latest period whose start is on or before it. */
    public Optional<RatePeriod> rateAt(LocalDate date) {
        return rates.stream()
                .filter(r -> !r.effectiveFrom().isAfter(date))
                .max(Comparator.comparing(RatePeriod::effectiveFrom));
    }
}
