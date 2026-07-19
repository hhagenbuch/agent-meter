package io.github.hhagenbuch.meter.core.budget;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * A calendar-aligned budget window (UTC). {@link #key(Instant)} returns the bucket an
 * instant falls into; when the clock crosses into the next bucket, accumulated spend is
 * naturally zero again — so a degraded scope recovers automatically at window rollover.
 */
public enum Window {

    HOURLY(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH'Z'")),
    DAILY(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
    MONTHLY(DateTimeFormatter.ofPattern("yyyy-MM"));

    private final DateTimeFormatter format;

    Window(DateTimeFormatter format) {
        this.format = format;
    }

    public String key(Instant at) {
        ZonedDateTime utc = at.atZone(ZoneOffset.UTC);
        return format.format(utc);
    }
}
