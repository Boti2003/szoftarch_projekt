package hu.bme.szoftarch.social.domain.port;

import java.util.Map;

/**
 * A participant's answer to a single duel exercise, forwarded to the Learning
 * service for authoritative scoring.
 *
 * <p>Learning scores by {@code position} (the exercise's index in the duel set)
 * and expects a type-specific answer JSON object (e.g.
 * {@code {"selectedOptionIds":["a"]}} for multiple choice). To keep the domain
 * free of any JSON library, {@code answer} is a plain {@link Map} the API layer
 * builds from the request; the adapter serializes it as the answer body.
 *
 * @param position the exercise position being answered (0-based)
 * @param answer   the type-specific answer payload; never {@code null}
 */
public record AnswerSubmission(int position, Map<String, Object> answer) {

    public AnswerSubmission {
        answer = (answer == null) ? Map.of() : Map.copyOf(answer);
    }
}
