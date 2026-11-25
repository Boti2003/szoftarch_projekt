package hu.bme.szoftarch.learning.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * A napi kihívás üzleti időzónája (TERV 5.4, D17). A {@code businessTimezone} a
 * compose-ból/env-ből jön ({@code BUSINESS_TIMEZONE}, alapértelmezés
 * {@code Europe/Budapest}); a napi kihívás üzleti napja e zóna szerint 00:00-kor
 * vált.
 *
 * @param businessTimezone IANA időzóna-azonosító (pl. {@code Europe/Budapest})
 */
@ConfigurationProperties(prefix = "learning.daily")
public record DailyProperties(String businessTimezone) {
}
