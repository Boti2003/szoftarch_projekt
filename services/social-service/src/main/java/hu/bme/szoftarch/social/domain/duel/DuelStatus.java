package hu.bme.szoftarch.social.domain.duel;

/**
 * Lifecycle status of a duel.
 *
 * <p>Legal edges (enforced by the duel state machine, task 2.5):
 * {@link #PENDING} to {@link #REJECTED}, {@link #ACCEPTED}, or {@link #EXPIRED};
 * {@link #ACCEPTED} to {@link #IN_PROGRESS}; {@link #IN_PROGRESS} to
 * {@link #COMPLETED}; and {@link #ACCEPTED} or {@link #IN_PROGRESS} to
 * {@link #EXPIRED}.
 */
public enum DuelStatus {
    PENDING,
    ACCEPTED,
    IN_PROGRESS,
    COMPLETED,
    REJECTED,
    EXPIRED
}
