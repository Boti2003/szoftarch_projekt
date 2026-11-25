package hu.bme.szoftarch.learning;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import hu.bme.szoftarch.learning.domain.daily.BusinessClock;

import org.junit.jupiter.api.Test;

/**
 * Az üzleti nap (budapesti) meghatározásának domain tesztje (TERV 5.4, D17). A
 * tárolás UTC-ben, de a {@code challenge_date} a budapesti üzleti nap dátuma —
 * UTC késő este, ami Budapesten már a következő nap, a következő dátumot adja.
 */
class BusinessClockTest {

    private final BusinessClock clock = new BusinessClock(ZoneId.of("Europe/Budapest"));

    @Test
    void lateUtcEveningIsAlreadyNextBusinessDayInBudapest() {
        // 2025-03-14 23:30 UTC. Budapesten (téli idő, UTC+1) ez már 2025-03-15 00:30,
        // tehát az üzleti nap a következő naptári nap.
        Instant lateUtc = Instant.parse("2025-03-14T23:30:00Z");

        assertThat(clock.today(lateUtc)).isEqualTo(LocalDate.of(2025, 3, 15));
    }

    @Test
    void earlyUtcMorningIsSameBusinessDayInBudapest() {
        // 2025-03-14 06:00 UTC -> Budapesten 07:00, ugyanaz a nap.
        Instant morningUtc = Instant.parse("2025-03-14T06:00:00Z");

        assertThat(clock.today(morningUtc)).isEqualTo(LocalDate.of(2025, 3, 14));
    }

    @Test
    void summerTimeOffsetAppliesTwoHourShift() {
        // Nyári idő (UTC+2): 2025-07-14 22:30 UTC -> Budapesten 2025-07-15 00:30.
        Instant lateSummerUtc = Instant.parse("2025-07-14T22:30:00Z");

        assertThat(clock.today(lateSummerUtc)).isEqualTo(LocalDate.of(2025, 7, 15));
    }
}
