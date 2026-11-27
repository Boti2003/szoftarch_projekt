package hu.bme.szoftarch.learning.infrastructure.persistence;

import java.util.Optional;
import java.util.Set;

import hu.bme.szoftarch.learning.domain.exercise.AnswerMismatchException;
import hu.bme.szoftarch.learning.domain.exercise.ExerciseContentException;
import hu.bme.szoftarch.learning.domain.exercise.ExercisePayload;
import hu.bme.szoftarch.learning.domain.exercise.ExerciseSolution;
import hu.bme.szoftarch.learning.domain.exercise.ExerciseTypeContent;
import hu.bme.szoftarch.learning.domain.exercise.NotAutoEvaluableException;
import hu.bme.szoftarch.learning.domain.exercise.UserAnswer;
import hu.bme.szoftarch.learning.domain.model.ExerciseType;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import org.springframework.stereotype.Component;

/**
 * Feladattípus → payload/solution record feloldás, deszerializálás és Bean
 * Validation egy helyen (TERV 6.2). A nyers {@link JsonNode} JSONB-ből a
 * {@link ExerciseTypeContent} leképezés szerinti típusos recordot állít elő, és
 * ellenőrzi a Bean Validation szabályokat.
 *
 * <p>Deszerializálási vagy validációs hiba esetén
 * {@link ExerciseContentException}-t dob, amelyből a hibakezelő egyértelmű,
 * stacktrace nélküli választ ad (nem 500 nyers stacktrace a kliensnek).</p>
 */
@Component
public class ExerciseContentCodec {

    private final ObjectMapper objectMapper = JsonbObjectMapperHolder.instance();
    private final Validator validator;

    public ExerciseContentCodec(Validator validator) {
        this.validator = validator;
    }

    /**
     * A nyers payload JSON-t a feladattípushoz tartozó {@link ExercisePayload}
     * recordra deszerializálja és validálja.
     *
     * @throws ExerciseContentException ha a JSON hiányzik, nem deszerializálható
     *     vagy megsérti a validációs szabályokat
     */
    public ExercisePayload readPayload(ExerciseType type, JsonNode payload) {
        Class<? extends ExercisePayload> payloadType = ExerciseTypeContent.of(type).payloadType();
        if (payload == null || payload.isNull()) {
            throw new ExerciseContentException("Missing payload for exercise type " + type);
        }
        ExercisePayload value = deserialize(type, "payload", payload, payloadType);
        validate(type, "payload", value);
        return value;
    }

    /**
     * A nyers solution JSON-t a feladattípushoz tartozó
     * {@link ExerciseSolution} recordra deszerializálja és validálja. Az
     * AI-típusoknak ({@code TRANSLATION}, {@code AI_SCENARIO}) nincs solutionje,
     * ezért üres eredményt ad.
     *
     * @throws ExerciseContentException ha a típusnak van solutionje, de hiányzik,
     *     nem deszerializálható vagy érvénytelen
     */
    public Optional<ExerciseSolution> readSolution(ExerciseType type, JsonNode solution) {
        Optional<Class<? extends ExerciseSolution>> solutionType = ExerciseTypeContent.of(type).solutionType();
        if (solutionType.isEmpty()) {
            return Optional.empty();
        }
        if (solution == null || solution.isNull()) {
            throw new ExerciseContentException("Missing solution for exercise type " + type);
        }
        ExerciseSolution value = deserialize(type, "solution", solution, solutionType.get());
        validate(type, "solution", value);
        return Optional.of(value);
    }

    /**
     * A kliens nyers válasz JSON-ját a feladattípushoz tartozó
     * {@link UserAnswer} recordra deszerializálja és validálja. A válasz a
     * kliens bemenete, ezért a hibaágak NEM szerveroldali
     * {@link ExerciseContentException}-t adnak, hanem kezelt, 400-ra fordítható
     * jelzést.
     *
     * @throws NotAutoEvaluableException ha a típusnak nincs {@link UserAnswer}
     *     recordja (AI-típusok)
     * @throws AnswerMismatchException   ha a válasz JSON hiányzik, nem
     *     deszerializálható vagy megsérti a validációs szabályokat
     */
    public UserAnswer readAnswer(ExerciseType type, JsonNode answer) {
        Optional<Class<? extends UserAnswer>> answerType = ExerciseTypeContent.of(type).answerType();
        if (answerType.isEmpty()) {
            throw new NotAutoEvaluableException(type);
        }
        if (answer == null || answer.isNull()) {
            throw new AnswerMismatchException("Missing answer for exercise type " + type);
        }
        UserAnswer value = deserializeAnswer(type, answer, answerType.get());
        validateAnswer(type, value);
        return value;
    }

    private UserAnswer deserializeAnswer(ExerciseType type, JsonNode node,
            Class<? extends UserAnswer> targetType) {
        try {
            return objectMapper.treeToValue(node, targetType);
        } catch (RuntimeException e) {
            throw new AnswerMismatchException("Unable to deserialize answer for exercise type " + type);
        }
    }

    private void validateAnswer(ExerciseType type, UserAnswer value) {
        Set<ConstraintViolation<UserAnswer>> violations = validator.validate(value);
        if (!violations.isEmpty()) {
            String details = violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .sorted()
                    .reduce((a, b) -> a + "; " + b)
                    .orElse("");
            throw new AnswerMismatchException(
                    "Invalid answer for exercise type " + type + ": " + details);
        }
    }

    private <T> T deserialize(ExerciseType type, String field, JsonNode node, Class<T> targetType) {
        try {
            return objectMapper.treeToValue(node, targetType);
        } catch (RuntimeException e) {
            throw new ExerciseContentException(
                    "Unable to deserialize " + field + " for exercise type " + type, e);
        }
    }

    private <T> void validate(ExerciseType type, String field, T value) {
        Set<ConstraintViolation<T>> violations = validator.validate(value);
        if (!violations.isEmpty()) {
            String details = violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .sorted()
                    .reduce((a, b) -> a + "; " + b)
                    .orElse("");
            throw new ExerciseContentException(
                    "Invalid " + field + " for exercise type " + type + ": " + details);
        }
    }
}
