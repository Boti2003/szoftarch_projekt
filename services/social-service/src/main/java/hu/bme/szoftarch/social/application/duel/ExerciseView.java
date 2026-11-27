package hu.bme.szoftarch.social.application.duel;

import java.util.List;
import java.util.UUID;

/**
 * Application-layer read model for a single duel exercise as presented to a
 * participant, without the correct solution.
 *
 * <p>Built from a {@link hu.bme.szoftarch.social.domain.port.ExerciseRef}
 * returned by the Learning service. Both participants receive the same
 * exercises in the same {@code position} order. Learning scores answers by
 * {@code position} and expects a type-specific answer, so the view carries the
 * {@code position}, the exercise {@code type}, and the selectable {@link Option}s
 * (id + text) the client needs to build an answer.
 *
 * @param position   the exercise's 0-based position within the duel set
 * @param exerciseId the Learning-owned exercise id
 * @param type       the exercise type (e.g. {@code MULTIPLE_CHOICE})
 * @param prompt     the exercise prompt shown to the participant
 * @param options    the selectable options (empty for non-option types); never {@code null}
 * @param maxPoints  the maximum points awarded for a correct answer
 */
public record ExerciseView(
        int position,
        UUID exerciseId,
        String type,
        String prompt,
        List<Option> options,
        int maxPoints) {

    public ExerciseView {
        options = (options == null) ? List.of() : List.copyOf(options);
    }

    /**
     * A selectable option for an option-based exercise.
     *
     * @param id   the option id Learning expects in the answer
     * @param text the display text shown to the participant
     */
    public record Option(String id, String text) {
    }
}
