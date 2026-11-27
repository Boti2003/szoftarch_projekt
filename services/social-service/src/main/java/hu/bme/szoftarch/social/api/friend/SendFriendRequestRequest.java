package hu.bme.szoftarch.social.api.friend;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request body for {@code POST /api/v1/friend-requests}: the id of the user the
 * authenticated caller wants to befriend.
 *
 * <p>Only the {@code receiverId} is carried in the body. The sender is always
 * derived from the authenticated JWT {@code sub} claim in the controller and is
 * never read from the request (requirement 4.1).
 *
 * @param receiverId the target user's id; required
 */
public record SendFriendRequestRequest(@NotNull UUID receiverId) {
}
