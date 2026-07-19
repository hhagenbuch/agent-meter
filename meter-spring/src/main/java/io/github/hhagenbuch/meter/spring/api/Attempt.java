package io.github.hhagenbuch.meter.spring.api;

import io.github.hhagenbuch.meter.core.cost.TokenUsage;

/**
 * One HTTP attempt of a call. A retried call has several: you pay for every attempt, so
 * cost sums them all (DESIGN.md section 8) — that visibility is a feature, not noise.
 *
 * @param usage         tokens this attempt consumed (a failed attempt may still be zero)
 * @param httpStatus    HTTP status returned (e.g. 200, 429, 529, or 0 for a transport error)
 * @param durationMillis how long the attempt took
 * @param succeeded     whether this attempt produced the final response
 */
public record Attempt(TokenUsage usage, int httpStatus, long durationMillis, boolean succeeded) {

    public static Attempt ok(TokenUsage usage, long durationMillis) {
        return new Attempt(usage, 200, durationMillis, true);
    }
}
