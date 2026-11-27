package hu.bme.szoftarch.social.application.notification;

import hu.bme.szoftarch.social.domain.duel.DuelStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Lightweight read model for a duel that requires the authenticated user's
 * action, shown in the {@link NotificationSummaryView}.
 *
 * <p>An actionable duel is either a PENDING challenge the user (as opponent)
 * can accept or reject, or an IN_PROGRESS duel awaiting more of the user's own
 * answers (the user's participant status is not yet COMPLETED). The {@code status}
 * reflected here is the duel's stored status with lazy expiry applied in memory
 * only; expired duels are never actionable and are therefore excluded.
 *
 * @param duelId    the duel id
 * @param status    the duel status (PENDING or IN_PROGRESS) that makes it actionable
 * @param opponentId the other participant in the duel (the challenger when the
 *                   user is the opponent, otherwise the opponent)
 * @param expiresAt when the duel expires (UTC); null when not set
 */
public record ActionableDuelView(
        UUID duelId,
        DuelStatus status,
        UUID opponentId,
        Instant expiresAt) {
}
