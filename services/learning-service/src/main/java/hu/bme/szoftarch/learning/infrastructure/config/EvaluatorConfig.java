package hu.bme.szoftarch.learning.infrastructure.config;

import hu.bme.szoftarch.learning.domain.exercise.EvaluatorRegistry;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Az U3 {@link EvaluatorRegistry} Spring-bekötése. A registry tiszta domain
 * konstrukció; itt az 5 auto típus alapértelmezett evaluatoraival töltött
 * példányt tesszük bean-né az application réteg számára.
 */
@Configuration
public class EvaluatorConfig {

    @Bean
    public EvaluatorRegistry evaluatorRegistry() {
        return EvaluatorRegistry.withDefaults();
    }
}
