package hu.bme.szoftarch.social.domain.duel;

import hu.bme.szoftarch.social.domain.common.Clock;
import hu.bme.szoftarch.social.domain.common.DuelExpiredException;
import hu.bme.szoftarch.social.domain.common.ForbiddenException;
import hu.bme.szoftarch.social.domain.common.InvalidStateException;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Pure, Spring-free state machine governing the duel lifecycle.
 *
 * <p>Implements the design "Duel state machine" pseudocode. It is the single
 * writer of {@link DuelStatus}, the duel expiry instant, and the
 * {@code winnerId}. Time is read through the injectable UTC {@link Clock}, so
 * expiry is deterministic and testable.
 *
 * <h2>Legal edges</h2>
 * <ul>
 *   <li>PENDING to REJECTED, ACCEPTED, or EXPIRED</li>
 *   <li>ACCEPTED to IN_PROGRESS</li>
 *   <li>IN_PROGRESS to COMPLETED</li>
 *   <li>ACCEPTED or IN_PROGRESS to EXPIRED</li>
 * </ul>
 *
 * <h2>Expiry (lazy, idempotent)</h2>
 * Before any event is applied, expiry is evaluated: a PENDING duel at or past
 * its 72h window, or an ACCEPTED/IN_PROGRESS duel at or past its 7-day window,
 * transitions to EXPIRED. Re-evaluating an already-expired (or terminal) duel
 * changes nothing (idempotent).
 *
 * <h2>Error semantics</h2>
 * <ul>
 *   <li>Illegal event for the current status raises {@link InvalidStateException}
 *       (HTTP 409 {@code INVALID_STATE}); duel state is left unchanged.</li>
 *   <li>Acting on an expired duel raises {@link DuelExpiredException}
 *       (HTTP 409 {@code DUEL_EXPIRED}).</li>
 *   <li>An actor who is not the authorized party raises
 *       {@link ForbiddenException} (HTTP 403 {@code FORBIDDEN}); the actor
 *       check is applied <em>before</em> any state change so illegal actors
 *       never mutate state.</li>
 * </ul>
 *
 * <h2>Completion invariant</h2>
 * The duel becomes COMPLETED only when <em>both</em> participants are
 * COMPLETED, at which point {@code winnerId} is set exactly once via
 * {@link DuelScoring#determineWinner} (null meaning a draw).
 */
public final class DuelStateMachine {

    /** PENDING duels expire 72 hours after creation. */
    public static final Duration PENDING_WINDOW = Duration.ofHours(72);

    /** ACCEPTED/IN_PROGRESS duels run for a 7-day window after acceptance. */
    public static final Duration ACTIVE_WINDOW = Duration.ofDays(7);

    private final Clock clock;

    /**
     * @param clock the injectable UTC time source; must not be {@code null}
     * @throws NullPointerException if {@code clock} is {@code null}
     */
    public DuelStateMachine(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Evaluates expiry lazily without applying any event.
     *
     * <p>Idempotent: a PENDING duel at/after 72h or an ACCEPTED/IN_PROGRESS
     * duel at/after its 7-day window becomes EXPIRED; all other states are
     * unchanged. Safe to call repeatedly.
     *
     * @param duel the duel to evaluate; must not be {@code null}
     * @throws NullPointerException if {@code duel} is {@code null}
     */
    public void applyExpiry(Duel duel) {
        Objects.requireNonNull(duel, "duel must not be null");
        evaluateExpiry(duel, clock.now());
    }

    /**
     * Applies an event to the duel, enforcing legality, expiry, and actor
     * authorization, and mutating status/expiry/winner as required.
     *
     * @param duel    the duel to transition; must not be {@code null}
     * @param event   the event to apply; must not be {@code null}
     * @param actorId the acting user id (JWT {@code sub}); must not be
     *                {@code null}
     * @throws NullPointerException   if any argument is {@code null}
     * @throws ForbiddenException     if the actor is not the authorized party
     * @throws InvalidStateException  if the event is illegal for the current
     *                                status
     * @throws DuelExpiredException   if the duel has expired and the event is
     *                                not legal for an expired duel
     */
    public void apply(Duel duel, DuelEvent event, UUID actorId) {
        Objects.requireNonNull(duel, "duel must not be null");
        Objects.requireNonNull(event, "event must not be null");
        Objects.requireNonNull(actorId, "actorId must not be null");

        Instant now = clock.now();
        evaluateExpiry(duel, now);

        switch (event) {
            case ACCEPT -> accept(duel, actorId, now);
            case REJECT -> reject(duel, actorId, now);
            case START -> start(duel, actorId, now);
            case COMPLETE_PARTICIPANT -> completeParticipant(duel, actorId, now);
        }
    }

    private void evaluateExpiry(Duel duel, Instant now) {
        DuelStatus status = duel.status();
        if (status == DuelStatus.PENDING
                || status == DuelStatus.ACCEPTED
                || status == DuelStatus.IN_PROGRESS) {
            if (!now.isBefore(duel.expiresAt())) {
                duel.setStatus(DuelStatus.EXPIRED);
            }
        }
    }

    private void accept(Duel duel, UUID actorId, Instant now) {
        requireActor(actorId.equals(duel.opponentId()),
                "only the opponent may accept the duel");
        requireStatus(duel, DuelStatus.PENDING, "accept");
        duel.setStatus(DuelStatus.ACCEPTED);
        duel.setExpiresAt(now.plus(ACTIVE_WINDOW));
    }

    private void reject(Duel duel, UUID actorId, Instant now) {
        requireActor(actorId.equals(duel.opponentId()),
                "only the opponent may reject the duel");
        requireStatus(duel, DuelStatus.PENDING, "reject");
        duel.setStatus(DuelStatus.REJECTED);
    }

    private void start(Duel duel, UUID actorId, Instant now) {
        requireActor(duel.isParticipant(actorId),
                "only a participant may start the duel");
        if (duel.status() != DuelStatus.ACCEPTED && duel.status() != DuelStatus.IN_PROGRESS) {
            throwForStatus(duel, "start");
        }
        if (duel.status() == DuelStatus.ACCEPTED) {
            duel.setStatus(DuelStatus.IN_PROGRESS);
        }
        duel.participantOf(actorId).markStarted(now);
    }

    private void completeParticipant(Duel duel, UUID actorId, Instant now) {
        requireActor(duel.isParticipant(actorId),
                "only a participant may complete their part of the duel");
        requireStatus(duel, DuelStatus.IN_PROGRESS, "complete participant");
        duel.participantOf(actorId).markCompleted(now);
        if (duel.bothParticipantsCompleted()) {
            duel.setStatus(DuelStatus.COMPLETED);
            // winnerId is set exactly once, here, at the moment of completion.
            duel.setWinnerId(DuelScoring.determineWinner(
                    duel.challenger().userId(), duel.challenger().score(),
                    duel.opponent().userId(), duel.opponent().score()));
        }
    }

    private void requireActor(boolean authorized, String message) {
        if (!authorized) {
            throw new ForbiddenException(message);
        }
    }

    private void requireStatus(Duel duel, DuelStatus required, String action) {
        if (duel.status() != required) {
            throwForStatus(duel, action);
        }
    }

    /**
     * Raises the appropriate conflict for an event that is illegal in the
     * current status: {@link DuelExpiredException} when the duel is EXPIRED,
     * otherwise {@link InvalidStateException}. State is left unchanged.
     */
    private void throwForStatus(Duel duel, String action) {
        if (duel.status() == DuelStatus.EXPIRED) {
            throw new DuelExpiredException(
                    "cannot " + action + ": duel has expired");
        }
        throw new InvalidStateException(
                "cannot " + action + " a duel in status " + duel.status());
    }
}
