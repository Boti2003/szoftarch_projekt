package hu.bme.szoftarch.learning.infrastructure.config;

import hu.bme.szoftarch.learning.infrastructure.client.IdentityClientProperties;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Bekapcsolja a Spring ütemezést a pontjóváírás retry-pollerhez (TERV B – U5,
 * 7.8), és regisztrálja az Identity kliens konfigurációs property-jeit.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(IdentityClientProperties.class)
public class SchedulingConfig {
}
