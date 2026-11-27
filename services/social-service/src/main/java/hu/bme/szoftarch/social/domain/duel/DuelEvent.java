package hu.bme.szoftarch.social.domain.duel;

/**
 * Events driving the duel state machine (design "Duel state machine").
 *
 * <ul>
 *   <li>{@link #ACCEPT} — opponent accepts a PENDING challenge
 *       (PENDING to ACCEPTED, extends expiry to 7 days).</li>
 *   <li>{@link #REJECT} — opponent rejects a PENDING challenge
 *       (PENDING to REJECTED).</li>
 *   <li>{@link #START} — a participant requests the exercises
 *       (ACCEPTED to IN_PROGRESS, marks the participant started).</li>
 *   <li>{@link #COMPLETE_PARTICIPANT} — a participant finishes all exercises
 *       (marks the participant completed; completes the duel and sets the
 *       winner once both participants are completed).</li>
 * </ul>
 */
public enum DuelEvent {
    ACCEPT,
    REJECT,
    START,
    COMPLETE_PARTICIPANT
}
