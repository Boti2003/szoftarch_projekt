package hu.bme.szoftarch.social.domain.port;

import java.util.UUID;

/**
 * Reference to a duel exercise set created by the Learning service.
 *
 * <p>Returned by {@link LearningDuelPort#createDuelSet}; Social persists the
 * {@code setId} as the duel's {@code learning_set_id} and uses it to fetch
 * exercises and submit answers.
 *
 * @param setId         the Learning-owned duel-set id
 * @param exerciseCount the number of exercises in the set
 */
public record DuelSetRef(UUID setId, int exerciseCount) {
}
