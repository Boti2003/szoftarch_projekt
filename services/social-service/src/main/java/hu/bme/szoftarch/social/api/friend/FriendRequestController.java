package hu.bme.szoftarch.social.api.friend;

import hu.bme.szoftarch.social.api.CurrentUser;
import hu.bme.szoftarch.social.api.error.Idempotency;
import hu.bme.szoftarch.social.api.error.PageParams;
import hu.bme.szoftarch.social.api.error.PageResponse;
import hu.bme.szoftarch.social.application.common.PageQuery;
import hu.bme.szoftarch.social.application.friend.Direction;
import hu.bme.szoftarch.social.application.friend.FriendRequestService;
import hu.bme.szoftarch.social.application.friend.FriendRequestView;
import hu.bme.szoftarch.social.domain.friend.FriendRequestStatus;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for the friend-request lifecycle (requirements 1.6–1.9),
 * wiring {@link FriendRequestService} under {@code /api/v1/friend-requests}.
 *
 * <p>The acting user id is always taken from the authenticated JWT {@code sub}
 * claim via {@link CurrentUser#require(Jwt)} and never from the request body or
 * a path variable (requirement 4.1, property 11). The {@code senderId} of a new
 * request and the actor that may accept/reject a request are both the
 * {@code sub}; receiver-only enforcement for accept/reject lives in the service.
 *
 * <p>Send, accept, and reject are retryable mutations and therefore require an
 * {@code Idempotency-Key} header (requirement 9.1), validated via
 * {@link Idempotency#requireKey(String)} as the first step of the handler.
 */
@RestController
@RequestMapping("/api/v1/friend-requests")
public class FriendRequestController {

    private final FriendRequestService friendRequestService;

    public FriendRequestController(FriendRequestService friendRequestService) {
        this.friendRequestService = friendRequestService;
    }

    /**
     * Sends a friend request from the authenticated user to {@code receiverId}.
     *
     * @param jwt            the authenticated principal (actor = {@code sub})
     * @param idempotencyKey the required {@code Idempotency-Key} header
     * @param request        the validated body carrying the {@code receiverId}
     * @return 201 with the created {@link FriendRequestView}
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FriendRequestView send(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = Idempotency.HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody SendFriendRequestRequest request) {
        Idempotency.requireKey(idempotencyKey);
        UUID senderId = CurrentUser.require(jwt);
        return friendRequestService.send(senderId, request.receiverId());
    }

    /**
     * Lists the authenticated user's friend requests filtered by direction and
     * status, paged.
     *
     * @param jwt       the authenticated principal (actor = {@code sub})
     * @param direction {@code incoming} (user is receiver) or {@code outgoing}
     *                  (user is sender)
     * @param status    the lifecycle status to filter by
     * @param page      the optional zero-based page index
     * @param size      the optional page size (default 20, capped at 100)
     * @return 200 with a {@link PageResponse} of {@link FriendRequestView}
     */
    @GetMapping
    public PageResponse<FriendRequestView> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam Direction direction,
            @RequestParam FriendRequestStatus status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        UUID userId = CurrentUser.require(jwt);
        PageQuery query = PageParams.toQuery(page, size);
        return PageResponse.from(friendRequestService.list(userId, direction, status, query));
    }

    /**
     * Accepts a pending friend request; only its receiver may do so (enforced in
     * the service). Retryable mutation requiring {@code Idempotency-Key}.
     *
     * @param jwt            the authenticated principal (actor = {@code sub})
     * @param id             the request id to accept
     * @param idempotencyKey the required {@code Idempotency-Key} header
     * @return 204 No Content
     */
    @PostMapping("/{id}/accept")
    public ResponseEntity<Void> accept(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @RequestHeader(value = Idempotency.HEADER, required = false) String idempotencyKey) {
        Idempotency.requireKey(idempotencyKey);
        UUID userId = CurrentUser.require(jwt);
        friendRequestService.accept(userId, id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Rejects a pending friend request; only its receiver may do so (enforced in
     * the service). Retryable mutation requiring {@code Idempotency-Key}.
     *
     * @param jwt            the authenticated principal (actor = {@code sub})
     * @param id             the request id to reject
     * @param idempotencyKey the required {@code Idempotency-Key} header
     * @return 204 No Content
     */
    @PostMapping("/{id}/reject")
    public ResponseEntity<Void> reject(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @RequestHeader(value = Idempotency.HEADER, required = false) String idempotencyKey) {
        Idempotency.requireKey(idempotencyKey);
        UUID userId = CurrentUser.require(jwt);
        friendRequestService.reject(userId, id);
        return ResponseEntity.noContent().build();
    }
}
