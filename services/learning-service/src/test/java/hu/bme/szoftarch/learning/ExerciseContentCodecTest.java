package hu.bme.szoftarch.learning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hu.bme.szoftarch.learning.domain.exercise.ExerciseContentException;
import hu.bme.szoftarch.learning.domain.exercise.ExercisePayload;
import hu.bme.szoftarch.learning.domain.exercise.ExerciseSolution;
import hu.bme.szoftarch.learning.domain.exercise.MultipleChoicePayload;
import hu.bme.szoftarch.learning.domain.exercise.MultipleChoiceSolution;
import hu.bme.szoftarch.learning.domain.exercise.TranslationPayload;
import hu.bme.szoftarch.learning.domain.model.ExerciseType;
import hu.bme.szoftarch.learning.infrastructure.persistence.ExerciseContentCodec;
import hu.bme.szoftarch.learning.support.ExerciseSamples;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * A payload/solution JSONB → record deszerializáció és Bean Validation unit
 * tesztje (U2) — DB nélkül. Lefedi mind a 7 típus érvényes payloadját, az
 * AI-típusok hiányzó solutionjét, és a negatív (érvénytelen) eseteket.
 */
class ExerciseContentCodecTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static ValidatorFactory validatorFactory;
    private static ExerciseContentCodec codec;

    @BeforeAll
    static void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        Validator validator = validatorFactory.getValidator();
        codec = new ExerciseContentCodec(validator);
    }

    @AfterAll
    static void tearDown() {
        validatorFactory.close();
    }

    private static JsonNode json(String raw) {
        return MAPPER.readTree(raw);
    }

    @Test
    void deserializesAndValidatesEveryTypePayload() {
        for (ExerciseSamples.Sample sample : ExerciseSamples.ALL) {
            ExerciseType type = ExerciseType.valueOf(sample.type());
            ExercisePayload payload = codec.readPayload(type, json(sample.payloadJson()));
            assertThat(payload).isNotNull();
        }
    }

    @Test
    void deserializesExactSolutionsAndSkipsAiTypes() {
        for (ExerciseSamples.Sample sample : ExerciseSamples.ALL) {
            ExerciseType type = ExerciseType.valueOf(sample.type());
            if (sample.solutionJson() == null) {
                // AI-típusok: nincs egzakt solution record.
                assertThat(codec.readSolution(type, null)).isEmpty();
            } else {
                var solution = codec.readSolution(type, json(sample.solutionJson()));
                assertThat(solution).isPresent();
                assertThat(solution.get()).isInstanceOf(ExerciseSolution.class);
            }
        }
    }

    @Test
    void deserializesTypedRecordFields() {
        MultipleChoicePayload payload = (MultipleChoicePayload) codec.readPayload(
                ExerciseType.MULTIPLE_CHOICE, json(ExerciseSamples.MULTIPLE_CHOICE.payloadJson()));
        assertThat(payload.options()).hasSize(3);
        assertThat(payload.options().getFirst().id()).isEqualTo("a");

        MultipleChoiceSolution solution = (MultipleChoiceSolution) codec.readSolution(
                ExerciseType.MULTIPLE_CHOICE, json(ExerciseSamples.MULTIPLE_CHOICE.solutionJson())).orElseThrow();
        assertThat(solution.correctOptionIds()).containsExactly("b");

        TranslationPayload translation = (TranslationPayload) codec.readPayload(
                ExerciseType.TRANSLATION, json(ExerciseSamples.TRANSLATION.payloadJson()));
        assertThat(translation.targetLanguageCode()).isEqualTo("en");
    }

    @Test
    void rejectsPayloadFailingBeanValidation() {
        // Egyetlen opció: megsérti a @Size(min = 2) szabályt.
        JsonNode invalid = json("{\"multiSelect\": false, \"options\": [{\"id\": \"a\", \"text\": \"only\"}]}");
        assertThatThrownBy(() -> codec.readPayload(ExerciseType.MULTIPLE_CHOICE, invalid))
                .isInstanceOf(ExerciseContentException.class)
                .hasMessageContaining("Invalid payload");
    }

    @Test
    void rejectsPayloadFailingNestedBeanValidation() {
        // Üres option id: megsérti a beágyazott @NotBlank szabályt.
        JsonNode invalid = json("""
                {"multiSelect": false, "options": [{"id": "", "text": "a"}, {"id": "b", "text": "b"}]}""");
        assertThatThrownBy(() -> codec.readPayload(ExerciseType.MULTIPLE_CHOICE, invalid))
                .isInstanceOf(ExerciseContentException.class)
                .hasMessageContaining("Invalid payload");
    }

    @Test
    void rejectsMalformedPayloadStructure() {
        // A tokens egy objektum helyett string: nem deszerializálható.
        JsonNode invalid = json("{\"tokens\": \"not-an-array\"}");
        assertThatThrownBy(() -> codec.readPayload(ExerciseType.ERROR_FINDING, invalid))
                .isInstanceOf(ExerciseContentException.class);
    }

    @Test
    void rejectsMissingPayload() {
        assertThatThrownBy(() -> codec.readPayload(ExerciseType.TRUE_FALSE, null))
                .isInstanceOf(ExerciseContentException.class)
                .hasMessageContaining("Missing payload");
    }

    @Test
    void rejectsMissingSolutionForExactType() {
        assertThatThrownBy(() -> codec.readSolution(ExerciseType.TRUE_FALSE, null))
                .isInstanceOf(ExerciseContentException.class)
                .hasMessageContaining("Missing solution");
    }
}
