package hu.bme.szoftarch.social.application.notification;

import java.time.Instant;
import java.util.UUID;

/**
 * Lightweight read model for a pending incoming friend request shown in the
 * {@link NotificationSummaryView}.
 *
 * <p>Carries only what the polling notification endpoint needs to render an
 * actionable item: the request id, who sent it, and when. The authenticated
 * user is always the receiver, so the receiver id is implied and omitted.
 *
 * @param requestId the pending friend request id
 * @param senderId  the user who sent the request
 * @param createdAt when the request was created (UTC)
 */
public record PendingRequestView(
        UUID requestId,
        UUID senderId,
        Instant createdAt) {
}
