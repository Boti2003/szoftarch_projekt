package hu.bme.szoftarch.social.application.duel;

import java.util.List;
import java.util.UUID;

/**
 * Application-layer read model for a single duel exercise as presented to a
 * participant, without the correct solution.
 *
 * <p>Built from a {@link hu.bme.szoftarch.social.domain.port.ExerciseRef}
 * returned by the Learning service. Both participants receive the same
 * exercises in the same order (requirement 3.11), so this view carries no
 * per-viewer state.
 *
 * @param exerciseId the Learning-owned exercise id
 * @param prompt     the exercise prompt shown to the participant
 * @param options    the selectable answer options (empty for free-text
 *                   exercises); never {@code null}
 * @param maxPoints  the maximum points awarded for a correct answer
 */
public record ExerciseView(UUID exerciseId, String prompt, List<String> options, int maxPoints) {

    public ExerciseView {
        options = (options == null) ? List.of() : List.copyOf(options);
    }
}
