package hu.bme.szoftarch.social.application.duel;

import hu.bme.szoftarch.social.domain.duel.DuelStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Compact application-layer read model for one duel in the authenticated
 * user's duel list ({@link DuelService#listMyDuels}).
 *
 * <p>It carries only list-level fields: the duel identity, both participant
 * ids, the language, the duel-level {@link DuelStatus}, the viewer's own
 * {@link hu.bme.szoftarch.social.domain.duel.ParticipantStatus} (as
 * {@code myStatus}), the (nullable) winner, and the expiry instant. No
 * opponent scores are exposed in the summary, so there is nothing to gate at
 * this level (gating applies to the detail view only).
 *
 * @param id           the duel id
 * @param challengerId the challenger's user id
 * @param opponentId   the opponent's user id
 * @param languageId   the duel language id
 * @param status       the duel-level status
 * @param myStatus     the requesting user's own participant status
 * @param winnerId     the winner's user id, or {@code null} when not yet
 *                     COMPLETED or when the completed duel was a draw
 * @param expiresAt    the expiry instant (UTC)
 */
public record DuelSummaryView(
        UUID id,
        UUID challengerId,
        UUID opponentId,
        UUID languageId,
        DuelStatus status,
        hu.bme.szoftarch.social.domain.duel.ParticipantStatus myStatus,
        UUID winnerId,
        Instant expiresAt) {
}
