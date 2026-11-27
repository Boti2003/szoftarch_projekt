package hu.bme.szoftarch.social.application.duel;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Application-layer read model for a single duel exercise as presented to a
 * participant, without the correct solution.
 *
 * <p>Carries the {@code position} (how Learning scores), the exercise
 * {@code type}, a convenience {@link Option} list for option-based types, and
 * the full type-specific {@code content} (statement, tokens, left/right items,
 * options, ...) so the UI can render the correct input for every exercise type
 * and build the matching answer.
 *
 * @param position   the exercise's 0-based position within the duel set
 * @param exerciseId the Learning-owned exercise id
 * @param type       the exercise type (e.g. {@code MULTIPLE_CHOICE})
 * @param prompt     the exercise prompt shown to the participant
 * @param options    convenience options for option-based types; never {@code null}
 * @param content    the raw type-specific presentation content; never {@code null}
 * @param maxPoints  the maximum points awarded for a correct answer
 */
public record ExerciseView(
        int position,
        UUID exerciseId,
        String type,
        String prompt,
        List<Option> options,
        Map<String, Object> content,
        int maxPoints) {

    public ExerciseView {
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
