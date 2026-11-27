package hu.bme.szoftarch.social.domain.port;

import java.util.List;
import java.util.UUID;

/**
 * Presentation of a single duel exercise as served by the Learning service,
 * without the correct solution.
 *
 * <p>Returned by {@link LearningDuelPort#fetchExercises}. Both participants
 * receive the same exercises in the same order (design duel orchestration).
 * The {@code options} list is empty for free-text/translation exercises.
 *
 * @param exerciseId the Learning-owned exercise id
 * @param prompt     the exercise prompt shown to the participant
 * @param options    the selectable answer options (empty when not applicable);
 *                   never {@code null}
 * @param maxPoints  the maximum points awarded for a correct answer
 */
public record ExerciseRef(UUID exerciseId, String prompt, List<String> options, int maxPoints) {

    public ExerciseRef {
        options = (options == null) ? List.of() : List.copyOf(options);
    }
}
