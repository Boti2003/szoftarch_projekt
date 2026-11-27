package hu.bme.szoftarch.social;

import hu.bme.szoftarch.social.domain.common.Clock;
import hu.bme.szoftarch.social.domain.duel.DuelStateMachine;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class SocialApplication {

    public static void main(String[] args) {
        SpringApplication.run(SocialApplication.class, args);
    }

    /**
     * Production {@link Clock} backed by the system UTC clock, injected into the
     * application services and the duel state machine so all time handling is
     * UTC and overridable in tests.
     */
    @Bean
    Clock systemClock() {
        return Clock.systemUtc();
    }

    /**
     * The duel {@link DuelStateMachine}, driven by the application {@link Clock}
     * so all duel lifecycle transitions and expiry evaluation share the same UTC
     * time source. The application services apply events through this single
     * writer of duel status, expiry, and winner.
     */
    @Bean
    DuelStateMachine duelStateMachine(Clock clock) {
        return new DuelStateMachine(clock);
    }
}
