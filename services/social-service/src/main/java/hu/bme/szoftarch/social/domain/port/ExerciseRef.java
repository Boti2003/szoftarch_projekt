package hu.bme.szoftarch.social.domain.port;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Presentation of a single duel exercise as served by the Learning service,
 * without the correct solution. Both participants receive the same exercises in
 * the same {@code position} order.
 *
 * <p>Learning scores duel answers by {@code position} and expects a
 * type-specific answer. To let the UI render the right input for every type
 * (not just option lists), the exercise carries the full type-specific
 * {@code content} exactly as Learning returned it (statement, tokens, left/right
 * items, options, ...), as an opaque {@link Map} so the domain stays free of any
 * JSON library. {@code options} is kept as a convenience for option-based types.
 *
 * @param position   the exercise's 0-based position within the duel set
 * @param exerciseId the Learning-owned exercise id
 * @param type       the exercise type (e.g. {@code MULTIPLE_CHOICE})
 * @param prompt     the exercise prompt shown to the participant
 * @param options    convenience list of selectable options (empty when the type
 *                   is not option-based); never {@code null}
 * @param content    the raw type-specific presentation content; never {@code null}
 * @param maxPoints  the maximum points awarded for a correct answer
 */
public record ExerciseRef(
        int position,
        UUID exerciseId,
        String type,
        String prompt,
        List<Option> options,
        Map<String, Object> content,
        int maxPoints) {

    public ExerciseRef {
        options = (options == null) ? List.of() : List.copyOf(options);
        content = (content == null) ? Map.of() : Map.copyOf(content);
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
