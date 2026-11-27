package hu.bme.szoftarch.social.application.duel;

import hu.bme.szoftarch.social.domain.duel.DuelStatus;
import hu.bme.szoftarch.social.domain.duel.ParticipantStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Application-layer read model for a single duel's detail, with the opponent's
 * result <em>gated</em> by the requesting participant's own completion.
 *
 * <p>Result-privacy rule (requirement 3.6, design Property 9): while the
 * requesting participant's own status is not {@link ParticipantStatus#COMPLETED},
 * {@code opponent} is {@code null} (the opponent's score, answered count and
 * participant status are withheld). Once the viewer's own status is
 * {@code COMPLETED}, the opponent's full {@link ParticipantResult} is exposed.
 * The viewer's own {@code self} result is always present. The gating is
 * enforced server-side in {@link DuelService#get}, not in the UI.
 *
 * <p>The viewer's role within the duel is identified by {@code viewerId}; the
 * {@code challengerId}/{@code opponentId} identify the two sides so the API
 * layer can label them.
 *
 * @param id           the duel id
 * @param challengerId the challenger's user id
 * @param opponentId   the opponent's user id
 * @param languageId   the duel language id
 * @param status       the duel-level status
 * @param winnerId     the winner's user id, or {@code null} when not COMPLETED
 *                     or when the completed duel was a draw
 * @param expiresAt    the expiry instant (UTC)
 * @param viewerId     the requesting user's id (always a participant)
 * @param self         the requesting user's own result; never {@code null}
 * @param opponent     the other participant's result, or {@code null} while the
 *                     viewer's own status is not COMPLETED (gated)
 */
public record DuelDetailView(
        UUID id,
        UUID challengerId,
        UUID opponentId,
        UUID languageId,
        DuelStatus status,
        UUID winnerId,
        Instant expiresAt,
        UUID viewerId,
        ParticipantResult self,
        ParticipantResult opponent) {

    /**
     * One participant's result within a duel detail.
     *
     * @param userId        the participant's user id
     * @param status        the participant's status
     * @param score         the participant's total score
     * @param answeredCount the number of exercises answered
     */
    public record ParticipantResult(
            UUID userId,
            ParticipantStatus status,
            int score,
            int answeredCount) {
    }
}
