package hu.bme.szoftarch.learning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import hu.bme.szoftarch.learning.domain.exercise.AnswerMismatchException;
import hu.bme.szoftarch.learning.domain.exercise.ErrorFindingAnswer;
import hu.bme.szoftarch.learning.domain.exercise.ErrorFindingEvaluator;
import hu.bme.szoftarch.learning.domain.exercise.ErrorFindingSolution;
import hu.bme.szoftarch.learning.domain.exercise.EvaluationResult;
import hu.bme.szoftarch.learning.domain.exercise.MatchingAnswer;
import hu.bme.szoftarch.learning.domain.exercise.MatchingEvaluator;
import hu.bme.szoftarch.learning.domain.exercise.MatchingSolution;
import hu.bme.szoftarch.learning.domain.exercise.MultipleChoiceAnswer;
import hu.bme.szoftarch.learning.domain.exercise.MultipleChoiceEvaluator;
import hu.bme.szoftarch.learning.domain.exercise.MultipleChoiceSolution;
import hu.bme.szoftarch.learning.domain.exercise.OddOneOutAnswer;
import hu.bme.szoftarch.learning.domain.exercise.OddOneOutEvaluator;
import hu.bme.szoftarch.learning.domain.exercise.OddOneOutSolution;
import hu.bme.szoftarch.learning.domain.exercise.TrueFalseAnswer;
import hu.bme.szoftarch.learning.domain.exercise.TrueFalseEvaluator;
import hu.bme.szoftarch.learning.domain.exercise.TrueFalseSolution;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * A Strategy evaluatorok unit tesztje (U3), típusonként helyes / részleges /
 * hibás válaszra, DB és Spring nélkül. Egyúttal ellenőrzi, hogy a feedback
 * soha nem szivárogtatja ki a helyes megoldást.
 */
class EvaluatorTest {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(EvaluationResult.SCALE);
    private static final BigDecimal ONE = BigDecimal.ONE.setScale(EvaluationResult.SCALE);

    @Nested
    class MultipleChoice {

        private final MultipleChoiceEvaluator evaluator = new MultipleChoiceEvaluator();
        private final MultipleChoiceSolution solution =
                new MultipleChoiceSolution(Set.of("opt-1", "opt-3"));

        @Test
        void correctAnswerScoresFull() {
            EvaluationResult result =
                    evaluator.evaluate(solution, new MultipleChoiceAnswer(Set.of("opt-1", "opt-3")));
            assertThat(result.correct()).isTrue();
            assertThat(result.scoreRatio()).isEqualByComparingTo(ONE);
        }

        @Test
        void wrongAnswerScoresZero() {
            EvaluationResult result =
                    evaluator.evaluate(solution, new MultipleChoiceAnswer(Set.of("opt-1", "opt-2")));
            assertThat(result.correct()).isFalse();
            assertThat(result.scoreRatio()).isEqualByComparingTo(ZERO);
        }

        @Test
        void emptyAnswerScoresZeroWithoutLeakingSolution() {
            EvaluationResult result =
                    evaluator.evaluate(solution, new MultipleChoiceAnswer(Set.of()));
            assertThat(result.correct()).isFalse();
            assertThat(result.scoreRatio()).isEqualByComparingTo(ZERO);
            assertThat(result.feedback()).doesNotContain("opt-1").doesNotContain("opt-3");
        }
    }

    @Nested
    class TrueFalse {

        private final TrueFalseEvaluator evaluator = new TrueFalseEvaluator();

        @Test
        void correctAnswerScoresFull() {
            EvaluationResult result =
                    evaluator.evaluate(new TrueFalseSolution(true), new TrueFalseAnswer(true));
            assertThat(result.correct()).isTrue();
            assertThat(result.scoreRatio()).isEqualByComparingTo(ONE);
        }

        @Test
        void wrongAnswerScoresZero() {
            EvaluationResult result =
                    evaluator.evaluate(new TrueFalseSolution(true), new TrueFalseAnswer(false));
            assertThat(result.correct()).isFalse();
            assertThat(result.scoreRatio()).isEqualByComparingTo(ZERO);
        }
    }

    @Nested
    class OddOneOut {

        private final OddOneOutEvaluator evaluator = new OddOneOutEvaluator();
        private final OddOneOutSolution solution = new OddOneOutSolution("x3");

        @Test
        void correctAnswerScoresFull() {
            EvaluationResult result = evaluator.evaluate(solution, new OddOneOutAnswer("x3"));
            assertThat(result.correct()).isTrue();
            assertThat(result.scoreRatio()).isEqualByComparingTo(ONE);
        }

        @Test
        void wrongAnswerScoresZero() {
            EvaluationResult result = evaluator.evaluate(solution, new OddOneOutAnswer("x1"));
            assertThat(result.correct()).isFalse();
            assertThat(result.scoreRatio()).isEqualByComparingTo(ZERO);
        }

        @Test
        void nullSelectionScoresZeroWithoutLeakingSolution() {
            EvaluationResult result = evaluator.evaluate(solution, new OddOneOutAnswer(null));
            assertThat(result.correct()).isFalse();
            assertThat(result.scoreRatio()).isEqualByComparingTo(ZERO);
            assertThat(result.feedback()).doesNotContain("x3");
        }
    }

    @Nested
    class Matching {

        private final MatchingEvaluator evaluator = new MatchingEvaluator();
        private final MatchingSolution solution = new MatchingSolution(List.of(
                new MatchingSolution.Pair("l1", "r1"),
                new MatchingSolution.Pair("l2", "r2"),
                new MatchingSolution.Pair("l3", "r3"),
                new MatchingSolution.Pair("l4", "r4")));

        @Test
        void fullMatchScoresFull() {
            EvaluationResult result = evaluator.evaluate(solution, new MatchingAnswer(List.of(
                    new MatchingAnswer.Pair("l1", "r1"),
                    new MatchingAnswer.Pair("l2", "r2"),
                    new MatchingAnswer.Pair("l3", "r3"),
                    new MatchingAnswer.Pair("l4", "r4"))));
            assertThat(result.correct()).isTrue();
            assertThat(result.scoreRatio()).isEqualByComparingTo(ONE);
        }

        @Test
        void partialMatchIsNotCorrectButScoresRatio() {
            EvaluationResult result = evaluator.evaluate(solution, new MatchingAnswer(List.of(
                    new MatchingAnswer.Pair("l1", "r1"),
                    new MatchingAnswer.Pair("l2", "r2"),
                    new MatchingAnswer.Pair("l3", "r4"),
                    new MatchingAnswer.Pair("l4", "r3"))));
            assertThat(result.correct()).isFalse();
            assertThat(result.scoreRatio()).isEqualByComparingTo(new BigDecimal("0.5000"));
        }

        @Test
        void wrongMatchScoresZero() {
            EvaluationResult result = evaluator.evaluate(solution, new MatchingAnswer(List.of(
                    new MatchingAnswer.Pair("l1", "r2"),
                    new MatchingAnswer.Pair("l2", "r1"),
                    new MatchingAnswer.Pair("l3", "r4"),
                    new MatchingAnswer.Pair("l4", "r3"))));
            assertThat(result.correct()).isFalse();
            assertThat(result.scoreRatio()).isEqualByComparingTo(ZERO);
        }

        @Test
        void emptyAnswerScoresZeroWithoutLeakingSolution() {
            EvaluationResult result = evaluator.evaluate(solution, new MatchingAnswer(List.of()));
            assertThat(result.correct()).isFalse();
            assertThat(result.scoreRatio()).isEqualByComparingTo(ZERO);
            assertThat(result.feedback()).doesNotContain("r1").doesNotContain("l1");
        }

        @Test
        void allCorrectPairsPlusBogusPairIsNotCorrect() {
            // Minden elvárt pár helyes, de egy fölös (túljelölt) pár is érkezik:
            // a teljes-párosítás szabály miatt correct=false, az arány < 1.0.
            EvaluationResult result = evaluator.evaluate(solution, new MatchingAnswer(List.of(
                    new MatchingAnswer.Pair("l1", "r1"),
                    new MatchingAnswer.Pair("l2", "r2"),
                    new MatchingAnswer.Pair("l3", "r3"),
                    new MatchingAnswer.Pair("l4", "r4"),
                    new MatchingAnswer.Pair("lX", "rX"))));
            assertThat(result.correct()).isFalse();
            assertThat(result.scoreRatio()).isLessThan(ONE);
        }
    }

    @Nested
    class ErrorFinding {

        private final ErrorFindingEvaluator evaluator = new ErrorFindingEvaluator();
        private final ErrorFindingSolution solution =
                new ErrorFindingSolution(Set.of(2, 5, 7, 9));

        @Test
        void exactMatchScoresFull() {
            EvaluationResult result =
                    evaluator.evaluate(solution, new ErrorFindingAnswer(Set.of(2, 5, 7, 9)));
            assertThat(result.correct()).isTrue();
            assertThat(result.scoreRatio()).isEqualByComparingTo(ONE);
        }

        @Test
        void partialMatchIsNotCorrectButScoresRatio() {
            EvaluationResult result =
                    evaluator.evaluate(solution, new ErrorFindingAnswer(Set.of(2, 5)));
            assertThat(result.correct()).isFalse();
            assertThat(result.scoreRatio()).isEqualByComparingTo(new BigDecimal("0.5000"));
        }

        @Test
        void falsePositivesReduceRatio() {
            // 2 helyes találat, 2 fals pozitív -> net 0 -> 0 arány
            EvaluationResult result =
                    evaluator.evaluate(solution, new ErrorFindingAnswer(Set.of(2, 5, 1, 3)));
            assertThat(result.correct()).isFalse();
            assertThat(result.scoreRatio()).isEqualByComparingTo(ZERO);
        }

        @Test
        void emptyAnswerScoresZeroWithoutLeakingSolution() {
            EvaluationResult result =
                    evaluator.evaluate(solution, new ErrorFindingAnswer(Set.of()));
            assertThat(result.correct()).isFalse();
            assertThat(result.scoreRatio()).isEqualByComparingTo(ZERO);
            assertThat(result.feedback()).doesNotContain("2").doesNotContain("5");
        }
    }

    @Test
    void mismatchedAnswerTypeRaisesHandledValidationError() {
        // helyes solution-típus, de a válasz másik típusé -> kezelt validációs hiba
        assertThatThrownBy(() -> new MultipleChoiceEvaluator()
                .evaluateErased(new MultipleChoiceSolution(Set.of("opt-1")), new TrueFalseAnswer(true)))
                .isInstanceOf(AnswerMismatchException.class);
    }
}
