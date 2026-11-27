package hu.bme.szoftarch.social.api.duel.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request body for {@code POST /api/v1/duels} (requirement 3.1).
 *
 * <p>Carries only the two values the challenger chooses: the {@code opponentId}
 * to challenge (must be an accepted friend, enforced server-side) and the
 * {@code languageId} for the duel set. The challenger's own id is <em>never</em>
 * taken from the body; it is derived from the JWT {@code sub} by the controller
 * (requirement 4.1). Both fields are mandatory; a missing value is rejected as a
 * 400 {@code VALIDATION_FAILED} by the global handler before the use case runs.
 *
 * @param opponentId the user to challenge; must be an accepted friend
 * @param languageId the language for the duel's exercise set
 */
public record CreateDuelRequest(
        @NotNull UUID opponentId,
        @NotNull UUID languageId) {
}
