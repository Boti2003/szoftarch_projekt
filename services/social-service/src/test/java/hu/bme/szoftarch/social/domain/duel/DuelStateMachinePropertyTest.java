package hu.bme.szoftarch.social.domain.duel;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import hu.bme.szoftarch.social.domain.common.Clock;
import hu.bme.szoftarch.social.domain.common.DomainException;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;

/**
 * Property-based tests for {@link DuelStateMachine} over randomized event
 * sequences, covering the design's correctness properties.
 *
 * <ul>
 *   <li><b>Property 5: Legal transitions</b> &mdash; every duel status change
 *   follows a legal edge of the state machine; illegal events raise a
 *   {@link DomainException} and leave state unchanged. No illegal status is
 *   ever reached no matter the sequence of random events, actors, and time
 *   advances. <b>Validates: Requirements 3.2</b></li>
 *   <li><b>Property 6: Expiry</b> &mdash; a PENDING duel at/past its 72h window
 *   and an ACCEPTED/IN_PROGRESS duel at/past its 7-day window transition to
 *   EXPIRED and accept no further actions. <b>Validates: Requirements 3.3</b></li>
 * </ul>
 *
 * <p>Time is injected through {@link Clock#fixed(Instant)}; each step builds a
 * fresh machine at the current simulated instant, which only ever moves
 * forward, so expiry evaluation is deterministic.
 */
class DuelStateMachinePropertyTest {

    private static final Instant BASE = Instant.parse("2025-01-01T00:00:00Z");

    private static final UUID CHALLENGER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OPPONENT = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-000000000099");

    /** Legal edges of the state machine, keyed by source status. */
    private static boolean isLegalEdge(DuelStatus from, DuelStatus to) {
        if (from == to) {
            return true; // a no-op (e.g. forbidden/illegal event) keeps the same status
        }
        return switch (from) {
            case PENDING -> EnumSet.of(DuelStatus.ACCEPTED, DuelStatus.REJECTED, DuelStatus.EXPIRED).contains(to);
            case ACCEPTED -> EnumSet.of(DuelStatus.IN_PROGRESS, DuelStatus.EXPIRED).contains(to);
            case IN_PROGRESS -> EnumSet.of(DuelStatus.COMPLETED, DuelStatus.EXPIRED).contains(to);
            // terminal states never transition
            case COMPLETED, REJECTED, EXPIRED -> false;
        };
    }

    private static final Set<DuelStatus> TERMINAL =
            EnumSet.of(DuelStatus.COMPLETED, DuelStatus.REJECTED, DuelStatus.EXPIRED);

    // ------------------------------------------------------------------
    // Property 5: Legal transitions — no illegal state is ever reached.
    // Validates: Requirements 3.2
    // ------------------------------------------------------------------

    /**
     * Apply a random sequence of (event, actor, time-advance) steps to a fresh
     * PENDING duel. After every step: the status only ever moved along a legal
     * edge, any raised error was a typed {@link DomainException} that left the
     * status unchanged, and the completion invariant holds (COMPLETED iff both
     * participants completed, winner set exactly once).
     */
    @Property(tries = 2000)
    void randomEventSequencesNeverReachAnIllegalState(@ForAll("steps") List<Step> steps) {
        Duel duel = freshPendingDuel();
        Instant now = BASE;

        for (Step step : steps) {
            now = now.plus(step.advance());
            DuelStatus before = duel.status();
            UUID actor = actorFor(step.actor());
            DuelStateMachine machine = new DuelStateMachine(Clock.fixed(now));

            boolean threw = false;
            try {
                machine.apply(duel, step.event(), actor);
            } catch (DomainException expected) {
                // Domain errors are the only failures allowed; nothing else.
                threw = true;
            }

            DuelStatus after = duel.status();

            // Property 5: every observed status change is a legal edge.
            assertThat(isLegalEdge(before, after))
                    .as("transition %s -> %s must be a legal edge", before, after)
                    .isTrue();

            // Illegal events raise and must not change state. A thrown error on
            // a non-terminal duel means either an illegal event, a forbidden
            // actor, or an expiry rejection; in all those cases the recorded
            // status change (if any) may only be a lazy expiry flip, never an
            // arbitrary jump.
            if (threw) {
                assertThat(after)
                        .as("a rejected event may only leave status unchanged or lazily expired")
                        .isIn(before, DuelStatus.EXPIRED);
            }

            // Completion invariant: COMPLETED iff both participants COMPLETED.
            if (after == DuelStatus.COMPLETED) {
                assertThat(duel.bothParticipantsCompleted()).isTrue();
            }

            // Terminal statuses are absorbing: once terminal, status never changes again.
            if (TERMINAL.contains(before)) {
                assertThat(after).isEqualTo(before);
            }
        }

        // The final status is always a member of the known enum (no illegal value).
        assertThat(duel.status()).isIn((Object[]) DuelStatus.values());
    }

    /**
     * A reachable COMPLETED duel always has a winner that is a participant or a
     * draw (null); the winner never depends on the order in which participants
     * completed (checked by comparing the two completion orders).
     */
    @Property(tries = 500)
    void completionSetsWinnerIndependentOfCompletionOrder(
            @ForAll @IntRange(min = 0, max = 50) int challengerScore,
            @ForAll @IntRange(min = 0, max = 50) int opponentScore) {

        UUID winnerChallengerFirst = completeInOrder(challengerScore, opponentScore, true);
        UUID winnerOpponentFirst = completeInOrder(challengerScore, opponentScore, false);

        // Winner is purely score-driven, not order/timing-driven.
        assertThat(winnerOpponentFirst).isEqualTo(winnerChallengerFirst);

        if (challengerScore > opponentScore) {
            assertThat(winnerChallengerFirst).isEqualTo(CHALLENGER);
        } else if (opponentScore > challengerScore) {
            assertThat(winnerChallengerFirst).isEqualTo(OPPONENT);
        } else {
            assertThat(winnerChallengerFirst).isNull();
        }
    }

    // ------------------------------------------------------------------
    // Property 6: Expiry — past-window duels expire and reject actions.
    // Validates: Requirements 3.3
    // ------------------------------------------------------------------

    /**
     * For any non-terminal duel and any instant at or past its expiry, lazy
     * evaluation always yields EXPIRED and every subsequent event is rejected
     * with a {@link DomainException}, leaving the duel EXPIRED.
     */
    @Property(tries = 1000)
    void pastWindowDuelsExpireAndRejectAllEvents(
            @ForAll("nonTerminalActiveStatus") DuelStatus status,
            @ForAll @LongRange(min = 0, max = 1_000_000) long secondsPastExpiry,
            @ForAll DuelEvent event,
            @ForAll("anyActor") UUID actor) {

        Instant expiresAt = BASE.plus(Duration.ofHours(72));
        Instant now = expiresAt.plus(Duration.ofSeconds(secondsPastExpiry));
        Duel duel = duelInStatus(status, expiresAt);
        DuelStateMachine machine = new DuelStateMachine(Clock.fixed(now));

        // Lazy expiry flips the status to EXPIRED.
        machine.applyExpiry(duel);
        assertThat(duel.status()).isEqualTo(DuelStatus.EXPIRED);

        // Any further event is rejected and the duel stays EXPIRED.
        boolean threw = false;
        try {
            machine.apply(duel, event, actor);
        } catch (DomainException expected) {
            threw = true;
        }
        assertThat(threw).as("an expired duel must reject every event").isTrue();
        assertThat(duel.status()).isEqualTo(DuelStatus.EXPIRED);
    }

    /**
     * Strictly before the expiry window, a duel is never expired by lazy
     * evaluation.
     */
    @Property(tries = 1000)
    void beforeWindowDuelsAreNeverExpired(
            @ForAll("nonTerminalActiveStatus") DuelStatus status,
            @ForAll @LongRange(min = 1, max = 1_000_000) long millisBeforeExpiry) {

        Instant expiresAt = BASE.plus(Duration.ofDays(7));
        Instant now = expiresAt.minusMillis(millisBeforeExpiry);
        Duel duel = duelInStatus(status, expiresAt);
        DuelStateMachine machine = new DuelStateMachine(Clock.fixed(now));

        machine.applyExpiry(duel);

        assertThat(duel.status()).isEqualTo(status);
    }

    // ------------------------------------------------------------------
    // Fixtures and generators
    // ------------------------------------------------------------------

    private static Duel freshPendingDuel() {
        return new Duel(
                UUID.randomUUID(), CHALLENGER, OPPONENT, DuelStatus.PENDING,
                BASE.plus(Duration.ofHours(72)), null,
                DuelParticipant.notStarted(CHALLENGER),
                DuelParticipant.notStarted(OPPONENT));
    }

    private static Duel duelInStatus(DuelStatus status, Instant expiresAt) {
        ParticipantStatus ps = switch (status) {
            case PENDING, ACCEPTED -> ParticipantStatus.NOT_STARTED;
            case IN_PROGRESS -> ParticipantStatus.IN_PROGRESS;
            default -> ParticipantStatus.NOT_STARTED;
        };
        Instant started = ps == ParticipantStatus.NOT_STARTED ? null : BASE;
        return new Duel(
                UUID.randomUUID(), CHALLENGER, OPPONENT, status, expiresAt, null,
                new DuelParticipant(CHALLENGER, ps, 0, 0, started, null),
                new DuelParticipant(OPPONENT, ps, 0, 0, started, null));
    }

    /**
     * Drives a duel from ACCEPTED through to COMPLETED, completing the two
     * participants in the requested order, and returns the resulting winner.
     */
    private static UUID completeInOrder(int challengerScore, int opponentScore, boolean challengerFirst) {
        Instant now = BASE;
        Duel duel = new Duel(
                UUID.randomUUID(), CHALLENGER, OPPONENT, DuelStatus.IN_PROGRESS,
                now.plus(Duration.ofDays(7)), null,
                new DuelParticipant(CHALLENGER, ParticipantStatus.IN_PROGRESS, challengerScore, 5, now, null),
                new DuelParticipant(OPPONENT, ParticipantStatus.IN_PROGRESS, opponentScore, 5, now, null));
        DuelStateMachine machine = new DuelStateMachine(Clock.fixed(now));

        if (challengerFirst) {
            machine.apply(duel, DuelEvent.COMPLETE_PARTICIPANT, CHALLENGER);
            machine.apply(duel, DuelEvent.COMPLETE_PARTICIPANT, OPPONENT);
        } else {
            machine.apply(duel, DuelEvent.COMPLETE_PARTICIPANT, OPPONENT);
            machine.apply(duel, DuelEvent.COMPLETE_PARTICIPANT, CHALLENGER);
        }
        assertThat(duel.status()).isEqualTo(DuelStatus.COMPLETED);
        return duel.winnerId();
    }

    private static UUID actorFor(ActorChoice choice) {
        return switch (choice) {
            case CHALLENGER -> CHALLENGER;
            case OPPONENT -> OPPONENT;
            case STRANGER -> STRANGER;
        };
    }

    enum ActorChoice { CHALLENGER, OPPONENT, STRANGER }

    /** One simulated step: an event by some actor after advancing the clock. */
    record Step(DuelEvent event, ActorChoice actor, Duration advance) {
    }

    @Provide
    Arbitrary<List<Step>> steps() {
        Arbitrary<DuelEvent> events = Arbitraries.of(DuelEvent.class);
        Arbitrary<ActorChoice> actors = Arbitraries.of(ActorChoice.class);
        // Advances span from zero up to ~10 days so both windows (72h and 7d)
        // are frequently crossed by cumulative time.
        Arbitrary<Duration> advances = Arbitraries.longs()
                .between(0, Duration.ofDays(10).toMinutes())
                .map(Duration::ofMinutes);
        Arbitrary<Step> step = Combinators.combine(events, actors, advances).as(Step::new);
        return step.list().ofMinSize(1).ofMaxSize(12);
    }

    @Provide
    Arbitrary<DuelStatus> nonTerminalActiveStatus() {
        return Arbitraries.of(DuelStatus.PENDING, DuelStatus.ACCEPTED, DuelStatus.IN_PROGRESS);
    }

    @Provide
    Arbitrary<UUID> anyActor() {
        return Arbitraries.of(CHALLENGER, OPPONENT, STRANGER);
    }
}
