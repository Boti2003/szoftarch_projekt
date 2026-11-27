package hu.bme.szoftarch.social.application.duel;

import hu.bme.szoftarch.social.domain.duel.DuelStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Application-layer read model returned by
 * {@link DuelService#create} after a duel is created.
 *
 * <p>Carries the duel identity and the fields the challenger needs to act on
 * the freshly-created PENDING duel: the two participant ids, the selected
 * {@code languageId}, the Learning {@code learningSetId}, the current
 * {@link DuelStatus}, and the expiry instant (72h for a PENDING duel). It
 * exposes no per-participant scores, so there is nothing to gate here.
 *
 * @param id            the duel id
 * @param challengerId  the challenger's user id
 * @param opponentId    the opponent's user id
 * @param languageId    the challenger-selected language id
 * @param learningSetId the Learning duel-set reference ({@code null} only if
 *                      set creation has not completed)
 * @param status        the current duel status (PENDING right after creation)
 * @param expiresAt     the expiry instant (UTC)
 */
public record DuelView(
        UUID id,
        UUID challengerId,
        UUID opponentId,
        UUID languageId,
        UUID learningSetId,
        DuelStatus status,
        Instant expiresAt) {
}
