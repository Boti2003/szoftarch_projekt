package hu.bme.szoftarch.learning.infrastructure.config;

import java.time.ZoneId;

import hu.bme.szoftarch.learning.domain.daily.BusinessClock;
import hu.bme.szoftarch.learning.infrastructure.client.AiClientProperties;
import hu.bme.szoftarch.learning.infrastructure.client.IdentityClientProperties;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Bekapcsolja a Spring ütemezést a pontjóváírás és a napi bónusz retry-
 * pollerekhez (TERV B – U5/U6, 7.8), és regisztrálja az Identity kliens, illetve
 * a napi kihívás ({@link DailyProperties}) konfigurációs property-jeit. A
 * {@link BusinessClock} domain konstrukció Spring-bekötését is itt adjuk.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties({IdentityClientProperties.class, DailyProperties.class,
        AiClientProperties.class})
public class SchedulingConfig {

    /**
     * Az üzleti nap órája az {@code learning.daily.business-timezone} alapján
     * (TERV 5.4, D17).
     */
    @Bean
    public BusinessClock businessClock(DailyProperties properties) {
        return new BusinessClock(ZoneId.of(properties.businessTimezone()));
    }
}
