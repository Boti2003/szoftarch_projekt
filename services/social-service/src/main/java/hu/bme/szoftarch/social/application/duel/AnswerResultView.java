package hu.bme.szoftarch.social.application.duel;

import hu.bme.szoftarch.social.domain.duel.DuelStatus;
import hu.bme.szoftarch.social.domain.duel.ParticipantStatus;
import java.util.UUID;

/**
 * Application-layer read model returned by
 * {@link DuelAnswerOrchestrator#submitAnswer} after a single answer is scored.
 *
 * <p>It reports the outcome of the just-submitted answer together with the
 * requesting participant's own progress after the award is applied. It carries
 * <em>only</em> the acting user's own progress; the opponent's score and
 * progress are never included here, keeping result privacy consistent with
 * {@link DuelDetailView} (requirement 3.6, design Property 9). The duel-level
 * {@code duelStatus} and {@code winnerId} are only meaningful once the duel is
 * {@link DuelStatus#COMPLETED}; while the duel is still IN_PROGRESS
 * {@code winnerId} is {@code null}.
 *
 * <p>The duel awards competition points only; no Identity profile-point side
 * effect is produced (requirement 3.7, design Property 12), so there is no
 * profile-point field here.
 *
 * @param duelId            the duel the answer belongs to
 * @param correct           whether the submitted answer was correct
 * @param awardedPoints     points awarded for this answer ({@code 0} when
 *                          incorrect)
 * @param newScore          the acting participant's total score after the award
 * @param answeredCount     the acting participant's answered-exercise count
 *                          after this answer
 * @param participantStatus the acting participant's status after this answer
 *                          (IN_PROGRESS, or COMPLETED once all five are
 *                          answered)
 * @param duelStatus        the duel-level status after this answer
 * @param winnerId          the winner's user id when the duel is COMPLETED
 *                          (null for a draw or while the duel is not yet
 *                          COMPLETED)
 */
public record AnswerResultView(
        UUID duelId,
        boolean correct,
        int awardedPoints,
        int newScore,
        int answeredCount,
        ParticipantStatus participantStatus,
        DuelStatus duelStatus,
        UUID winnerId) {
}
