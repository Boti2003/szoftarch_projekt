package hu.bme.szoftarch.learning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import hu.bme.szoftarch.learning.domain.exercise.AnswerMismatchException;
import hu.bme.szoftarch.learning.domain.exercise.EvaluationResult;
import hu.bme.szoftarch.learning.domain.exercise.EvaluatorRegistry;
import hu.bme.szoftarch.learning.domain.exercise.MultipleChoiceAnswer;
import hu.bme.szoftarch.learning.domain.exercise.MultipleChoiceSolution;
import hu.bme.szoftarch.learning.domain.exercise.NotAutoEvaluableException;
import hu.bme.szoftarch.learning.domain.model.ExerciseType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Az {@link EvaluatorRegistry} unit tesztje (U3): mind az 5 auto típus
 * feloldható, a két AI-típus kezelt „nem auto" jelzést ad (nem nyers kivétel).
 */
class EvaluatorRegistryTest {

    private final EvaluatorRegistry registry = EvaluatorRegistry.withDefaults();

    @ParameterizedTest
    @EnumSource(value = ExerciseType.class,
            names = {"MULTIPLE_CHOICE", "TRUE_FALSE", "MATCHING", "ODD_ONE_OUT", "ERROR_FINDING"})
    void resolvesEveryAutoType(ExerciseType type) {
        assertThat(registry.isAutoEvaluable(type)).isTrue();
        assertThat(registry.find(type)).isPresent();
        assertThat(registry.find(type).orElseThrow().exerciseType()).isEqualTo(type);
    }

    @ParameterizedTest
    @EnumSource(value = ExerciseType.class, names = {"TRANSLATION", "AI_SCENARIO"})
    void aiTypesSignalNotAutoEvaluable(ExerciseType type) {
        assertThat(registry.isAutoEvaluable(type)).isFalse();
        assertThat(registry.find(type)).isEmpty();

        MultipleChoiceSolution solution = new MultipleChoiceSolution(Set.of("a"));
        MultipleChoiceAnswer answer = new MultipleChoiceAnswer(Set.of("a"));
        assertThatThrownBy(() -> registry.evaluate(type, solution, answer))
                .isInstanceOf(NotAutoEvaluableException.class)
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void evaluateDispatchesToCorrectStrategy() {
        EvaluationResult result = registry.evaluate(
                ExerciseType.MULTIPLE_CHOICE,
                new MultipleChoiceSolution(Set.of("a", "b")),
                new MultipleChoiceAnswer(Set.of("a", "b")));
        assertThat(result.correct()).isTrue();
    }

    @Test
    void evaluateRaisesHandledErrorOnMismatchedAnswer() {
        assertThatThrownBy(() -> registry.evaluate(
                ExerciseType.MULTIPLE_CHOICE,
                new MultipleChoiceSolution(Set.of("a")),
                new hu.bme.szoftarch.learning.domain.exercise.TrueFalseAnswer(true)))
                .isInstanceOf(AnswerMismatchException.class);
    }
}
