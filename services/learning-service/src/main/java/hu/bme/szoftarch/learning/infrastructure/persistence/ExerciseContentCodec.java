package hu.bme.szoftarch.learning.infrastructure.persistence;

import java.util.Optional;
import java.util.Set;

import hu.bme.szoftarch.learning.domain.exercise.ExerciseContentException;
import hu.bme.szoftarch.learning.domain.exercise.ExercisePayload;
import hu.bme.szoftarch.learning.domain.exercise.ExerciseSolution;
import hu.bme.szoftarch.learning.domain.exercise.ExerciseTypeContent;
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
