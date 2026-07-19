package io.github.hhagenbuch.meter.core.pricing;

import java.time.LocalDate;

/**
 * A price that takes effect on {@code effectiveFrom} and holds until a later period
 * supersedes it. Rates are per million tokens (MTok).
 *
 * @param cachedInputPerMTok rate for cache-read input tokens; {@code null} if the model
 *                           has no cached-input pricing
 */
public record RatePeriod(LocalDate effectiveFrom, double inputPerMTok, double outputPerMTok,
                         Double cachedInputPerMTok) {
}
