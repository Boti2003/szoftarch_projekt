package hu.bme.szoftarch.social.api.notification;

import hu.bme.szoftarch.social.api.CurrentUser;
import hu.bme.szoftarch.social.application.notification.NotificationService;
import hu.bme.szoftarch.social.application.notification.NotificationSummaryView;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller backing the polling notification summary (requirement 6.1).
 *
 * <p>The single endpoint {@code GET /api/v1/notifications/summary} returns the
 * authenticated user's pending incoming friend requests and actionable duels,
 * as computed by {@link NotificationService#summary(UUID)}. The path matches
 * {@code contracts/openapi/social-api.yaml} ({@code /notifications/summary}).
 * It is the read side of the 15-second frontend poll and performs no writes, so
 * no {@code Idempotency-Key} is required.
 *
 * <h2>Actor identity (requirement 4.1)</h2>
 * The user whose notifications are summarized is derived from the JWT
 * {@code sub} via {@link CurrentUser#require(Jwt)}, never from a request
 * parameter, so a caller only ever sees their own pending items.
 */
@RestController
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = Objects.requireNonNull(notificationService);
    }

    /**
     * Returns the authenticated user's notification summary.
     *
     * @param jwt the authenticated principal (summary subject = {@code sub})
     * @return the pending-request and actionable-duel summary
     */
    @GetMapping("/api/v1/notifications/summary")
    public NotificationSummaryView summary(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = CurrentUser.require(jwt);
        return notificationService.summary(userId);
    }
}
