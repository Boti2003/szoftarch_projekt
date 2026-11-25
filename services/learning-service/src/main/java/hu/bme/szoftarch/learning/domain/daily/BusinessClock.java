package hu.bme.szoftarch.learning.domain.daily;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Az üzleti nap meghatározása egy konfigurált időzóna szerint (TERV 5.4, D17).
 * A napi kihívás üzleti napja {@code Europe/Budapest} szerint 00:00-kor vált; a
 * tárolás UTC-ben marad, de a {@code challenge_date} a budapesti üzleti nap
 * dátuma. Spring-mentes domain konstrukció, így az időzóna-logika egységtesztben
 * is ellenőrizhető.
 */
public final class BusinessClock {

    private final ZoneId zone;

    public BusinessClock(ZoneId zone) {
        this.zone = Objects.requireNonNull(zone, "zone");
    }

    /**
     * @return a jelen pillanathoz tartozó üzleti nap dátuma az időzónában
     */
    public LocalDate today() {
        return today(Instant.now());
    }

    /**
     * @param instant egy UTC pillanat
     * @return az {@code instant}-hoz tartozó üzleti nap dátuma az időzónában
     *     (pl. UTC késő este, ami Budapesten már a következő nap, a következő
     *     dátumot adja)
     */
    public LocalDate today(Instant instant) {
        return LocalDate.ofInstant(instant, zone);
    }

    public ZoneId zone() {
        return zone;
    }
}
