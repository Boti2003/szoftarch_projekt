package hu.bme.szoftarch.social.domain.duel;

/**
 * Per-user progress status within a duel.
 *
 * <p>A participant starts {@link #NOT_STARTED}, moves to {@link #IN_PROGRESS}
 * when exercises are first requested, and reaches {@link #COMPLETED} after
 * answering all exercises in the duel set.
 */
public enum ParticipantStatus {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETED
}
