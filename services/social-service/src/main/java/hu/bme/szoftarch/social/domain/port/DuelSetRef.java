package hu.bme.szoftarch.social.domain.port;

import java.util.List;
import java.util.UUID;

/**
 * Reference to a duel exercise set created by the Learning service, together
 * with the ordered exercise presentations Learning returns in the same call.
 *
 * <p>Returned by {@link LearningDuelPort#createDuelSet}; Social persists the
 * {@code setId} as the duel's {@code learning_set_id}. The {@code exercises} are
 * in {@code position} order and shared identically by both participants.
 *
 * @param setId     the Learning-owned duel-set id
 * @param exercises the exercises in position order; never {@code null}
 */
public record DuelSetRef(UUID setId, List<ExerciseRef> exercises) {

    public DuelSetRef {
        exercises = (exercises == null) ? List.of() : List.copyOf(exercises);
    }
}
