package io.github.hhagenbuch.meter.core.budget;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class WindowTest {

    private final Instant t = Instant.parse("2026-07-19T10:30:00Z");

    @Test
    void bucketsByCalendarUnitInUtc() {
        assertThat(Window.HOURLY.key(t)).isEqualTo("2026-07-19T10Z");
        assertThat(Window.DAILY.key(t)).isEqualTo("2026-07-19");
        assertThat(Window.MONTHLY.key(t)).isEqualTo("2026-07");
    }

    @Test
    void nextHourIsANewHourlyBucketButSameDay() {
        Instant later = Instant.parse("2026-07-19T11:00:00Z");
        assertThat(Window.HOURLY.key(later)).isNotEqualTo(Window.HOURLY.key(t));
        assertThat(Window.DAILY.key(later)).isEqualTo(Window.DAILY.key(t));
    }
}
