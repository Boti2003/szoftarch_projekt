package hu.bme.szoftarch.social.domain.port;

import java.util.List;
import java.util.UUID;

/**
 * Presentation of a single duel exercise as served by the Learning service,
 * without the correct solution. Both participants receive the same exercises in
 * the same {@code position} order (design duel orchestration).
 *
 * <p>Learning scores duel answers by {@code position} (not exercise id) and
 * expects a type-specific answer, so this reference carries the {@code position},
 * the exercise {@code type}, and the selectable {@link Option}s (id + display
 * text) needed to build an answer. {@code options} is empty for exercise types
 * that are not option-based.
 *
 * @param position   the exercise's 0-based position within the duel set
 * @param exerciseId the Learning-owned exercise id
 * @param type       the exercise type (e.g. {@code MULTIPLE_CHOICE})
 * @param prompt     the exercise prompt shown to the participant
 * @param options    the selectable answer options (empty when not applicable);
 *                   never {@code null}
 * @param maxPoints  the maximum points awarded for a correct answer
 */
public record ExerciseRef(
        int position,
        UUID exerciseId,
        String type,
        String prompt,
        List<Option> options,
        int maxPoints) {

    public ExerciseRef {
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
