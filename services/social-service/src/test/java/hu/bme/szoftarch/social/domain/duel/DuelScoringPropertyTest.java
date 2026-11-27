package hu.bme.szoftarch.social.domain.duel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

/**
 * Property-based tests for {@link DuelScoring}, covering the design's
 * correctness properties over randomized inputs.
 *
 * <ul>
 *   <li><b>Property 7: Scoring</b> &mdash; per exercise, awarded points are the
 *   exercise's {@code maxPoints} iff the answer is correct, else {@code 0}, and
 *   a participant's total equals the sum of per-exercise awards.
 *   <b>Validates: Requirements 3.4</b></li>
 *   <li><b>Property 8: Winner determination</b> &mdash; {@code determineWinner}
 *   returns the higher-total participant, or {@code null} for a draw on equal
 *   totals, and never depends on timing. Because {@code determineWinner} takes
 *   only participant ids and scores (no timestamps), the result is asserted to
 *   be purely a function of the two scores.
 *   <b>Validates: Requirements 3.5</b></li>
 * </ul>
 */
class DuelScoringPropertyTest {

    // ---------------------------------------------------------------------
    // Property 7: Scoring
    // Validates: Requirements 3.4
    // ---------------------------------------------------------------------

    /**
     * Each answer is awarded exactly {@code maxPoints} when correct and
     * {@code 0} when incorrect.
     */
    @Property
    void scoreAnswerAwardsMaxPointsIffCorrect(@ForAll @IntRange(min = 0, max = 1_000_000) int maxPoints,
                                              @ForAll boolean correct) {
        int awarded = DuelScoring.scoreAnswer(maxPoints, correct);

        if (correct) {
            assertThat(awarded).isEqualTo(maxPoints);
        } else {
            assertThat(awarded).isZero();
        }
    }

    /**
     * Over a vector of exercises (each with its own {@code maxPoints} and a
     * random correct/incorrect outcome), every award is either {@code maxPoints}
     * or {@code 0}, and the participant total equals the sum of the per-exercise
     * awards.
     */
    @Property
    void participantTotalEqualsSumOfPerExerciseAwards(@ForAll("answerVectors") List<Answer> answers) {
        int expectedTotal = 0;

        for (Answer answer : answers) {
            int award = DuelScoring.scoreAnswer(answer.maxPoints(), answer.correct());

            // Per-exercise rule: max_points iff correct, else 0.
            assertThat(award).isEqualTo(answer.correct() ? answer.maxPoints() : 0);
            expectedTotal += award;
        }

        // Participant total is the sum of the per-exercise awards.
        assertThat(totalFor(answers)).isEqualTo(expectedTotal);
    }

    // ---------------------------------------------------------------------
    // Property 8: Winner determination
    // Validates: Requirements 3.5
    // ---------------------------------------------------------------------

    /**
     * The participant with the strictly higher total wins; equal totals yield a
     * draw ({@code null}).
     */
    @Property
    void higherTotalWinsEqualTotalsDraw(@ForAll @IntRange(min = 0, max = 500) int firstScore,
                                        @ForAll @IntRange(min = 0, max = 500) int secondScore) {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        UUID winner = DuelScoring.determineWinner(first, firstScore, second, secondScore);

        if (firstScore > secondScore) {
            assertThat(winner).isEqualTo(first);
        } else if (secondScore > firstScore) {
            assertThat(winner).isEqualTo(second);
        } else {
            assertThat(winner).isNull();
        }
    }

    /**
     * The winner determination never depends on timing. {@code determineWinner}
     * accepts only participant ids and scores &mdash; no {@code startedAt} or
     * {@code completedAt} input exists &mdash; so the result is purely a
     * function of the two scores. Repeated calls with the same ids and scores
     * are identical, demonstrating that no external (e.g. time-based) state can
     * influence the outcome.
     */
    @Property
    void winnerIsPureFunctionOfScoresNotTiming(@ForAll @IntRange(min = 0, max = 500) int firstScore,
                                               @ForAll @IntRange(min = 0, max = 500) int secondScore) {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        UUID firstCall = DuelScoring.determineWinner(first, firstScore, second, secondScore);
        UUID secondCall = DuelScoring.determineWinner(first, firstScore, second, secondScore);

        // Determinism: identical inputs always yield identical results, so no
        // timing or ambient state can affect the decision.
        assertThat(secondCall).isEqualTo(firstCall);
    }

    /**
     * Swapping the two participants (ids and their scores together) only swaps
     * the identity of the winner and never changes a draw into a win. This
     * confirms the outcome is governed solely by the score comparison, with no
     * positional or timing bias.
     */
    @Property
    void winnerIsSymmetricUnderParticipantSwap(@ForAll @IntRange(min = 0, max = 500) int firstScore,
                                               @ForAll @IntRange(min = 0, max = 500) int secondScore) {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        UUID winner = DuelScoring.determineWinner(first, firstScore, second, secondScore);
        UUID swapped = DuelScoring.determineWinner(second, secondScore, first, firstScore);

        // A draw stays a draw; otherwise the same participant wins regardless
        // of the argument order.
        assertThat(swapped).isEqualTo(winner);
    }

    /**
     * End-to-end over randomized answer vectors for two participants: the
     * winner determined from the summed awards matches a direct comparison of
     * those sums, tying Property 7's scoring to Property 8's winner rule.
     */
    @Property
    void winnerMatchesSummedAwardsOverVectors(@ForAll("answerVectors") List<Answer> firstAnswers,
                                              @ForAll("answerVectors") List<Answer> secondAnswers) {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        int firstTotal = totalFor(firstAnswers);
        int secondTotal = totalFor(secondAnswers);

        UUID winner = DuelScoring.determineWinner(first, firstTotal, second, secondTotal);

        if (firstTotal > secondTotal) {
            assertThat(winner).isEqualTo(first);
        } else if (secondTotal > firstTotal) {
            assertThat(winner).isEqualTo(second);
        } else {
            assertThat(winner).isNull();
        }
    }

    /**
     * Distinct participant ids are a precondition; passing the same id twice is
     * rejected regardless of the scores.
     */
    @Property
    void equalParticipantIdsAreRejected(@ForAll @IntRange(min = 0, max = 500) int firstScore,
                                        @ForAll @IntRange(min = 0, max = 500) int secondScore) {
        UUID shared = UUID.randomUUID();

        assertThatThrownBy(() -> DuelScoring.determineWinner(shared, firstScore, shared, secondScore))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static int totalFor(List<Answer> answers) {
        int total = 0;
        for (Answer answer : answers) {
            total += DuelScoring.scoreAnswer(answer.maxPoints(), answer.correct());
        }
        return total;
    }

    /**
     * A single answer: the exercise's maximum points and whether the submission
     * was correct.
     */
    record Answer(int maxPoints, boolean correct) {
    }

    /**
     * Random vectors of 1..5 answers (the duel exercise set is fixed at 5),
     * each with a non-negative {@code maxPoints} and a random correctness flag.
     * {@code maxPoints} is bounded well below {@code Integer.MAX_VALUE} so the
     * summed totals cannot overflow an {@code int}.
     */
    @Provide
    Arbitrary<List<Answer>> answerVectors() {
        Arbitrary<Integer> maxPoints = Arbitraries.integers().between(0, 1_000);
        Arbitrary<Boolean> correct = Arbitraries.of(true, false);
        Arbitrary<Answer> answer = Combinators.combine(maxPoints, correct).as(Answer::new);
        return answer.list().ofMinSize(1).ofMaxSize(5);
    }
}
