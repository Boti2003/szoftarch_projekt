package hu.bme.szoftarch.social.domain.duel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import hu.bme.szoftarch.social.domain.common.Clock;
import hu.bme.szoftarch.social.domain.common.DuelExpiredException;
import hu.bme.szoftarch.social.domain.common.ForbiddenException;
import hu.bme.szoftarch.social.domain.common.InvalidStateException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DuelStateMachine}, covering every legal and illegal
 * edge, both expiry windows, actor authorization, and idempotent expiry.
 *
 * <p>Relates to design Property 5 (Legal transitions) and Property 6 (Expiry).
 * <b>Validates: Requirements 3.2, 3.3</b>
 *
 * <p>Time is controlled deterministically through {@link Clock#fixed(Instant)}:
 * the state machine reads "now" exclusively from its injected clock, so a
 * machine built at a chosen instant exercises expiry and windowing precisely.
 */
class DuelStateMachineTest {

    private static final Instant BASE = Instant.parse("2025-01-01T00:00:00Z");

    private static final UUID CHALLENGER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OPPONENT = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-000000000099");

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** A state machine whose clock is frozen at {@code at}. */
    private static DuelStateMachine machineAt(Instant at) {
        return new DuelStateMachine(Clock.fixed(at));
    }

    /** A duel in the given status with a given expiry and fresh participants. */
    private static Duel duel(DuelStatus status, Instant expiresAt) {
        return new Duel(
                UUID.randomUUID(), CHALLENGER, OPPONENT, status, expiresAt, null,
                DuelParticipant.notStarted(CHALLENGER),
                DuelParticipant.notStarted(OPPONENT));
    }

    /** A duel with explicit participants (used to drive completion/winner cases). */
    private static Duel duel(DuelStatus status, Instant expiresAt,
                             DuelParticipant challenger, DuelParticipant opponent) {
        return new Duel(
                UUID.randomUUID(), CHALLENGER, OPPONENT, status, expiresAt, null,
                challenger, opponent);
    }

    private static DuelParticipant participant(UUID id, ParticipantStatus status, int score) {
        Instant started = status == ParticipantStatus.NOT_STARTED ? null : BASE;
        Instant completed = status == ParticipantStatus.COMPLETED ? BASE : null;
        return new DuelParticipant(id, status, score, 0, started, completed);
    }

    // ------------------------------------------------------------------
    // Legal edges
    // ------------------------------------------------------------------

    @Nested
    class LegalEdges {

        @Test
        void pendingAcceptMovesToAcceptedAndExtendsExpiryToSevenDays() {
            Instant now = BASE;
            Duel d = duel(DuelStatus.PENDING, now.plus(DuelStateMachine.PENDING_WINDOW));

            machineAt(now).apply(d, DuelEvent.ACCEPT, OPPONENT);

            assertThat(d.status()).isEqualTo(DuelStatus.ACCEPTED);
            assertThat(d.expiresAt()).isEqualTo(now.plus(DuelStateMachine.ACTIVE_WINDOW));
        }

        @Test
        void pendingRejectMovesToRejected() {
            Instant now = BASE;
            Duel d = duel(DuelStatus.PENDING, now.plus(DuelStateMachine.PENDING_WINDOW));

            machineAt(now).apply(d, DuelEvent.REJECT, OPPONENT);

            assertThat(d.status()).isEqualTo(DuelStatus.REJECTED);
        }

        @Test
        void acceptedStartMovesToInProgressAndStartsParticipant() {
            Instant now = BASE;
            Duel d = duel(DuelStatus.ACCEPTED, now.plus(DuelStateMachine.ACTIVE_WINDOW));

            machineAt(now).apply(d, DuelEvent.START, CHALLENGER);

            assertThat(d.status()).isEqualTo(DuelStatus.IN_PROGRESS);
            assertThat(d.participantOf(CHALLENGER).status()).isEqualTo(ParticipantStatus.IN_PROGRESS);
            assertThat(d.participantOf(CHALLENGER).startedAt()).isEqualTo(now);
        }

        @Test
        void inProgressStartBySecondParticipantStaysInProgress() {
            Instant now = BASE;
            DuelParticipant challenger = participant(CHALLENGER, ParticipantStatus.IN_PROGRESS, 0);
            DuelParticipant opponent = DuelParticipant.notStarted(OPPONENT);
            Duel d = duel(DuelStatus.IN_PROGRESS, now.plus(DuelStateMachine.ACTIVE_WINDOW),
                    challenger, opponent);

            machineAt(now).apply(d, DuelEvent.START, OPPONENT);

            assertThat(d.status()).isEqualTo(DuelStatus.IN_PROGRESS);
            assertThat(d.participantOf(OPPONENT).status()).isEqualTo(ParticipantStatus.IN_PROGRESS);
        }

        @Test
        void inProgressCompleteParticipantMarksParticipantCompletedButNotDuelUntilBoth() {
            Instant now = BASE;
            DuelParticipant challenger = participant(CHALLENGER, ParticipantStatus.IN_PROGRESS, 30);
            DuelParticipant opponent = participant(OPPONENT, ParticipantStatus.IN_PROGRESS, 10);
            Duel d = duel(DuelStatus.IN_PROGRESS, now.plus(DuelStateMachine.ACTIVE_WINDOW),
                    challenger, opponent);

            machineAt(now).apply(d, DuelEvent.COMPLETE_PARTICIPANT, CHALLENGER);

            assertThat(d.participantOf(CHALLENGER).status()).isEqualTo(ParticipantStatus.COMPLETED);
            assertThat(d.status()).isEqualTo(DuelStatus.IN_PROGRESS);
            assertThat(d.winnerId()).isNull();
        }

        @Test
        void inProgressCompleteBothParticipantsCompletesDuelAndSetsHigherScoreWinner() {
            Instant now = BASE;
            DuelParticipant challenger = participant(CHALLENGER, ParticipantStatus.IN_PROGRESS, 30);
            DuelParticipant opponent = participant(OPPONENT, ParticipantStatus.IN_PROGRESS, 10);
            Duel d = duel(DuelStatus.IN_PROGRESS, now.plus(DuelStateMachine.ACTIVE_WINDOW),
                    challenger, opponent);
            DuelStateMachine machine = machineAt(now);

            machine.apply(d, DuelEvent.COMPLETE_PARTICIPANT, CHALLENGER);
            machine.apply(d, DuelEvent.COMPLETE_PARTICIPANT, OPPONENT);

            assertThat(d.status()).isEqualTo(DuelStatus.COMPLETED);
            assertThat(d.winnerId()).isEqualTo(CHALLENGER);
        }

        @Test
        void completionWithEqualScoresIsADraw() {
            Instant now = BASE;
            DuelParticipant challenger = participant(CHALLENGER, ParticipantStatus.IN_PROGRESS, 20);
            DuelParticipant opponent = participant(OPPONENT, ParticipantStatus.IN_PROGRESS, 20);
            Duel d = duel(DuelStatus.IN_PROGRESS, now.plus(DuelStateMachine.ACTIVE_WINDOW),
                    challenger, opponent);
            DuelStateMachine machine = machineAt(now);

            machine.apply(d, DuelEvent.COMPLETE_PARTICIPANT, CHALLENGER);
            machine.apply(d, DuelEvent.COMPLETE_PARTICIPANT, OPPONENT);

            assertThat(d.status()).isEqualTo(DuelStatus.COMPLETED);
            assertThat(d.winnerId()).isNull();
        }
    }

    // ------------------------------------------------------------------
    // Illegal edges: raise InvalidStateException, leave state unchanged
    // ------------------------------------------------------------------

    @Nested
    class IllegalEdges {

        @Test
        void acceptIsIllegalWhenNotPending() {
            Instant now = BASE;
            Duel d = duel(DuelStatus.ACCEPTED, now.plus(DuelStateMachine.ACTIVE_WINDOW));

            assertThatThrownBy(() -> machineAt(now).apply(d, DuelEvent.ACCEPT, OPPONENT))
                    .isInstanceOf(InvalidStateException.class);
            assertThat(d.status()).isEqualTo(DuelStatus.ACCEPTED);
        }

        @Test
        void rejectIsIllegalWhenNotPending() {
            Instant now = BASE;
            Duel d = duel(DuelStatus.IN_PROGRESS, now.plus(DuelStateMachine.ACTIVE_WINDOW));

            assertThatThrownBy(() -> machineAt(now).apply(d, DuelEvent.REJECT, OPPONENT))
                    .isInstanceOf(InvalidStateException.class);
            assertThat(d.status()).isEqualTo(DuelStatus.IN_PROGRESS);
        }

        @Test
        void startIsIllegalWhenPending() {
            Instant now = BASE;
            Duel d = duel(DuelStatus.PENDING, now.plus(DuelStateMachine.PENDING_WINDOW));

            assertThatThrownBy(() -> machineAt(now).apply(d, DuelEvent.START, CHALLENGER))
                    .isInstanceOf(InvalidStateException.class);
            assertThat(d.status()).isEqualTo(DuelStatus.PENDING);
            assertThat(d.participantOf(CHALLENGER).status()).isEqualTo(ParticipantStatus.NOT_STARTED);
        }

        @Test
        void startIsIllegalWhenRejected() {
            Instant now = BASE;
            Duel d = duel(DuelStatus.REJECTED, now.plus(DuelStateMachine.PENDING_WINDOW));

            assertThatThrownBy(() -> machineAt(now).apply(d, DuelEvent.START, CHALLENGER))
                    .isInstanceOf(InvalidStateException.class);
            assertThat(d.status()).isEqualTo(DuelStatus.REJECTED);
        }

        @Test
        void completeParticipantIsIllegalWhenAccepted() {
            Instant now = BASE;
            Duel d = duel(DuelStatus.ACCEPTED, now.plus(DuelStateMachine.ACTIVE_WINDOW));

            assertThatThrownBy(() -> machineAt(now).apply(d, DuelEvent.COMPLETE_PARTICIPANT, CHALLENGER))
                    .isInstanceOf(InvalidStateException.class);
            assertThat(d.status()).isEqualTo(DuelStatus.ACCEPTED);
        }

        @Test
        void completeParticipantIsIllegalWhenCompleted() {
            Instant now = BASE;
            DuelParticipant challenger = participant(CHALLENGER, ParticipantStatus.COMPLETED, 30);
            DuelParticipant opponent = participant(OPPONENT, ParticipantStatus.COMPLETED, 10);
            Duel d = duel(DuelStatus.COMPLETED, now.plus(DuelStateMachine.ACTIVE_WINDOW),
                    challenger, opponent);

            assertThatThrownBy(() -> machineAt(now).apply(d, DuelEvent.COMPLETE_PARTICIPANT, CHALLENGER))
                    .isInstanceOf(InvalidStateException.class);
            assertThat(d.status()).isEqualTo(DuelStatus.COMPLETED);
        }

        @Test
        void anyEventIsIllegalOnRejectedTerminalState() {
            Instant now = BASE;
            Duel d = duel(DuelStatus.REJECTED, now.plus(DuelStateMachine.PENDING_WINDOW));

            assertThatThrownBy(() -> machineAt(now).apply(d, DuelEvent.ACCEPT, OPPONENT))
                    .isInstanceOf(InvalidStateException.class);
            assertThat(d.status()).isEqualTo(DuelStatus.REJECTED);
        }
    }

    // ------------------------------------------------------------------
    // Actor authorization: raise ForbiddenException before any state change
    // ------------------------------------------------------------------

    @Nested
    class ActorAuthorization {

        @Test
        void acceptByChallengerIsForbidden() {
            Instant now = BASE;
            Duel d = duel(DuelStatus.PENDING, now.plus(DuelStateMachine.PENDING_WINDOW));

            assertThatThrownBy(() -> machineAt(now).apply(d, DuelEvent.ACCEPT, CHALLENGER))
                    .isInstanceOf(ForbiddenException.class);
            assertThat(d.status()).isEqualTo(DuelStatus.PENDING);
        }

        @Test
        void rejectByChallengerIsForbidden() {
            Instant now = BASE;
            Duel d = duel(DuelStatus.PENDING, now.plus(DuelStateMachine.PENDING_WINDOW));

            assertThatThrownBy(() -> machineAt(now).apply(d, DuelEvent.REJECT, CHALLENGER))
                    .isInstanceOf(ForbiddenException.class);
            assertThat(d.status()).isEqualTo(DuelStatus.PENDING);
        }

        @Test
        void acceptByStrangerIsForbidden() {
            Instant now = BASE;
            Duel d = duel(DuelStatus.PENDING, now.plus(DuelStateMachine.PENDING_WINDOW));

            assertThatThrownBy(() -> machineAt(now).apply(d, DuelEvent.ACCEPT, STRANGER))
                    .isInstanceOf(ForbiddenException.class);
            assertThat(d.status()).isEqualTo(DuelStatus.PENDING);
        }

        @Test
        void startByStrangerIsForbidden() {
            Instant now = BASE;
            Duel d = duel(DuelStatus.ACCEPTED, now.plus(DuelStateMachine.ACTIVE_WINDOW));

            assertThatThrownBy(() -> machineAt(now).apply(d, DuelEvent.START, STRANGER))
                    .isInstanceOf(ForbiddenException.class);
            assertThat(d.status()).isEqualTo(DuelStatus.ACCEPTED);
        }

        @Test
        void completeParticipantByStrangerIsForbidden() {
            Instant now = BASE;
            DuelParticipant challenger = participant(CHALLENGER, ParticipantStatus.IN_PROGRESS, 0);
            DuelParticipant opponent = participant(OPPONENT, ParticipantStatus.IN_PROGRESS, 0);
            Duel d = duel(DuelStatus.IN_PROGRESS, now.plus(DuelStateMachine.ACTIVE_WINDOW),
                    challenger, opponent);

            assertThatThrownBy(() -> machineAt(now).apply(d, DuelEvent.COMPLETE_PARTICIPANT, STRANGER))
                    .isInstanceOf(ForbiddenException.class);
            assertThat(d.status()).isEqualTo(DuelStatus.IN_PROGRESS);
            assertThat(d.participantOf(CHALLENGER).status()).isEqualTo(ParticipantStatus.IN_PROGRESS);
            assertThat(d.participantOf(OPPONENT).status()).isEqualTo(ParticipantStatus.IN_PROGRESS);
        }
    }

    // ------------------------------------------------------------------
    // Expiry windows (Property 6)
    // ------------------------------------------------------------------

    @Nested
    class Expiry {

        @Test
        void pendingDuelExpiresExactlyAtSeventyTwoHourWindow() {
            Instant created = BASE;
            Instant expiresAt = created.plus(DuelStateMachine.PENDING_WINDOW);
            Duel d = duel(DuelStatus.PENDING, expiresAt);

            // At the exact boundary (now == expiresAt) the duel is expired.
            machineAt(expiresAt).applyExpiry(d);

            assertThat(d.status()).isEqualTo(DuelStatus.EXPIRED);
        }

        @Test
        void pendingDuelDoesNotExpireJustBeforeWindow() {
            Instant created = BASE;
            Instant expiresAt = created.plus(DuelStateMachine.PENDING_WINDOW);
            Duel d = duel(DuelStatus.PENDING, expiresAt);

            machineAt(expiresAt.minusMillis(1)).applyExpiry(d);

            assertThat(d.status()).isEqualTo(DuelStatus.PENDING);
        }

        @Test
        void acceptedDuelExpiresAtSevenDayWindow() {
            Instant acceptedAt = BASE;
            Instant expiresAt = acceptedAt.plus(DuelStateMachine.ACTIVE_WINDOW);
            Duel d = duel(DuelStatus.ACCEPTED, expiresAt);

            machineAt(expiresAt).applyExpiry(d);

            assertThat(d.status()).isEqualTo(DuelStatus.EXPIRED);
        }

        @Test
        void inProgressDuelExpiresAtSevenDayWindow() {
            Instant startedAt = BASE;
            Instant expiresAt = startedAt.plus(DuelStateMachine.ACTIVE_WINDOW);
            DuelParticipant challenger = participant(CHALLENGER, ParticipantStatus.IN_PROGRESS, 0);
            DuelParticipant opponent = participant(OPPONENT, ParticipantStatus.IN_PROGRESS, 0);
            Duel d = duel(DuelStatus.IN_PROGRESS, expiresAt, challenger, opponent);

            machineAt(expiresAt).applyExpiry(d);

            assertThat(d.status()).isEqualTo(DuelStatus.EXPIRED);
        }

        @Test
        void expiredPendingDuelRejectsFurtherActionsWithDuelExpired() {
            Instant created = BASE;
            Instant expiresAt = created.plus(DuelStateMachine.PENDING_WINDOW);
            Duel d = duel(DuelStatus.PENDING, expiresAt);

            // now past the window: accept is rejected with DUEL_EXPIRED.
            assertThatThrownBy(() -> machineAt(expiresAt.plus(Duration.ofHours(1)))
                    .apply(d, DuelEvent.ACCEPT, OPPONENT))
                    .isInstanceOf(DuelExpiredException.class);
            assertThat(d.status()).isEqualTo(DuelStatus.EXPIRED);
        }

        @Test
        void expiredActiveDuelRejectsFurtherActionsWithDuelExpired() {
            Instant acceptedAt = BASE;
            Instant expiresAt = acceptedAt.plus(DuelStateMachine.ACTIVE_WINDOW);
            Duel d = duel(DuelStatus.IN_PROGRESS, expiresAt,
                    participant(CHALLENGER, ParticipantStatus.IN_PROGRESS, 0),
                    participant(OPPONENT, ParticipantStatus.IN_PROGRESS, 0));

            assertThatThrownBy(() -> machineAt(expiresAt.plus(Duration.ofDays(1)))
                    .apply(d, DuelEvent.COMPLETE_PARTICIPANT, CHALLENGER))
                    .isInstanceOf(DuelExpiredException.class);
            assertThat(d.status()).isEqualTo(DuelStatus.EXPIRED);
        }

        @Test
        void expiryIsIdempotent() {
            Instant created = BASE;
            Instant expiresAt = created.plus(DuelStateMachine.PENDING_WINDOW);
            Duel d = duel(DuelStatus.PENDING, expiresAt);
            DuelStateMachine machine = machineAt(expiresAt.plus(Duration.ofHours(5)));

            machine.applyExpiry(d);
            DuelStatus afterFirst = d.status();
            machine.applyExpiry(d);
            DuelStatus afterSecond = d.status();

            assertThat(afterFirst).isEqualTo(DuelStatus.EXPIRED);
            assertThat(afterSecond).isEqualTo(DuelStatus.EXPIRED);
        }

        @Test
        void completedDuelIsNotAffectedByExpiryEvaluation() {
            Instant now = BASE;
            DuelParticipant challenger = participant(CHALLENGER, ParticipantStatus.COMPLETED, 30);
            DuelParticipant opponent = participant(OPPONENT, ParticipantStatus.COMPLETED, 10);
            // expiresAt in the past; a terminal duel must not flip to EXPIRED.
            Duel d = duel(DuelStatus.COMPLETED, now.minus(Duration.ofDays(30)),
                    challenger, opponent);

            machineAt(now).applyExpiry(d);

            assertThat(d.status()).isEqualTo(DuelStatus.COMPLETED);
        }

        @Test
        void rejectedDuelIsNotAffectedByExpiryEvaluation() {
            Instant now = BASE;
            Duel d = duel(DuelStatus.REJECTED, now.minus(Duration.ofDays(30)));

            machineAt(now).applyExpiry(d);

            assertThat(d.status()).isEqualTo(DuelStatus.REJECTED);
        }
    }
}
