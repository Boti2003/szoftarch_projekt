package hu.bme.szoftarch.social.api.friend;

import hu.bme.szoftarch.social.api.CurrentUser;
import hu.bme.szoftarch.social.api.error.Idempotency;
import hu.bme.szoftarch.social.api.error.PageParams;
import hu.bme.szoftarch.social.api.error.PageResponse;
import hu.bme.szoftarch.social.application.common.PageQuery;
import hu.bme.szoftarch.social.application.friend.FriendService;
import hu.bme.szoftarch.social.application.friend.FriendView;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for the authenticated user's friendships (requirements 2.2,
 * 2.4, 2.5), wiring {@link FriendService} under {@code /api/v1/friends}.
 *
 * <p>The acting user id is always taken from the authenticated JWT {@code sub}
 * claim via {@link CurrentUser#require(Jwt)} and never from the request body or
 * a path variable (requirement 4.1, property 11). On removal the {@code sub} is
 * the actor; membership/authorization is enforced in the service.
 *
 * <p>Friendship removal is a retryable mutation and therefore requires an
 * {@code Idempotency-Key} header (requirement 9.1); listing is a read and does
 * not.
 */
@RestController
@RequestMapping("/api/v1/friends")
public class FriendController {

    private final FriendService friendService;

    public FriendController(FriendService friendService) {
        this.friendService = friendService;
    }

    /**
     * Lists the authenticated user's friends with their public Identity stats,
     * paged.
     *
     * @param jwt  the authenticated principal (actor = {@code sub})
     * @param page the optional zero-based page index
     * @param size the optional page size (default 20, capped at 100)
     * @return 200 with a {@link PageResponse} of {@link FriendView}
     */
    @GetMapping
    public PageResponse<FriendView> listFriends(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        UUID userId = CurrentUser.require(jwt);
        PageQuery query = PageParams.toQuery(page, size);
        return PageResponse.from(friendService.listFriends(userId, query));
    }

    /**
     * Removes the friendship between the authenticated user and {@code friendId}
     * (symmetric removal); membership is enforced in the service. Retryable
     * mutation requiring {@code Idempotency-Key}.
     *
     * @param jwt            the authenticated principal (actor = {@code sub})
     * @param friendId       the other member of the friendship to remove
     * @param idempotencyKey the required {@code Idempotency-Key} header
     * @return 204 No Content
     */
    @DeleteMapping("/{friendId}")
    public ResponseEntity<Void> removeFriend(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID friendId,
            @RequestHeader(value = Idempotency.HEADER, required = false) String idempotencyKey) {
        Idempotency.requireKey(idempotencyKey);
        UUID userId = CurrentUser.require(jwt);
        friendService.removeFriend(userId, friendId);
        return ResponseEntity.noContent().build();
    }
}
