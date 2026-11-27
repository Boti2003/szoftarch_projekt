package hu.bme.szoftarch.social.application.notification;

import java.util.List;

/**
 * Read-only polling summary of the authenticated user's pending incoming
 * friend requests and actionable duels.
 *
 * <p>Returned by {@link NotificationService#summary}. It is a lightweight,
 * state-preserving snapshot intended for the frontend's periodic poll: it never
 * mutates stored state. Each count equals the size of its corresponding list.
 *
 * @param pendingFriendRequestCount number of pending incoming friend requests
 * @param pendingFriendRequests     the pending incoming friend requests
 * @param actionableDuelCount       number of duels awaiting the user's action
 * @param actionableDuels           the duels awaiting the user's action
 */
public record NotificationSummaryView(
        int pendingFriendRequestCount,
        List<PendingRequestView> pendingFriendRequests,
        int actionableDuelCount,
        List<ActionableDuelView> actionableDuels) {
}
