package hu.bme.szoftarch.social.domain.common;

import java.time.Instant;

/**
 * Injectable, UTC-based source of the current time for the domain layer.
 *
 * <p>The domain never reads the wall clock directly ({@code Instant.now()},
 * {@code System.currentTimeMillis()}); it depends on this abstraction so that
 * time-sensitive logic — notably duel expiry in the state machine — is
 * deterministic and testable. All instants are UTC; {@link Instant} is an
 * absolute point on the timeline and carries no zone, so callers that need a
 * wall-clock representation must explicitly apply {@code ZoneOffset.UTC}.
 *
 * <p>This is a pure domain port: it has no {@code org.springframework.*} or
 * JPA dependency. Infrastructure supplies the production implementation
 * (wrapping {@link java.time.Clock#systemUTC()}); tests supply a fixed or
 * advanceable implementation.
 */
@FunctionalInterface
public interface Clock {

    /**
     * Returns the current instant on the UTC timeline.
     *
     * @return the current {@link Instant}; never {@code null}
     */
    Instant now();

    /**
     * Returns a {@code Clock} backed by the system UTC clock.
     *
     * <p>Provided as a convenience default; production wiring may instead
     * register a bean that delegates to {@link java.time.Clock#systemUTC()}.
     *
     * @return a clock reading {@link java.time.Clock#systemUTC()}
     */
    static Clock systemUtc() {
        java.time.Clock system = java.time.Clock.systemUTC();
        return system::instant;
    }

    /**
     * Returns a {@code Clock} frozen at the given instant.
     *
     * <p>Useful in tests for deterministic expiry evaluation.
     *
     * @param fixed the instant this clock always reports; must not be
     *              {@code null}
     * @return a clock that always returns {@code fixed}
     * @throws NullPointerException if {@code fixed} is {@code null}
     */
    static Clock fixed(Instant fixed) {
        if (fixed == null) {
            throw new NullPointerException("fixed instant must not be null");
        }
        return () -> fixed;
    }
}
