package hu.bme.szoftarch.learning.domain.exercise;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

import hu.bme.szoftarch.learning.domain.model.ExerciseType;

/**
 * Hibakereső kiértékelés (TERV 5.1): a megjelölt hibás tokenindexek halmaza.
 * {@code correct} csak akkor igaz, ha a jelölt halmaz PONTOSAN egyezik a
 * solutionbeli hibás indexek halmazával.
 *
 * <p>Részleges {@code score_ratio}: a helyesen azonosított hibák aránya, a
 * tévesen megjelölt (fals pozitív) indexekkel büntetve. A pontarány a
 * {@code (helyes találat − fals pozitív)} nemnegatív értéke osztva az összes
 * hibás index számával — így a túljelölés nem ad teljes pontot.</p>
 */
public final class ErrorFindingEvaluator
        extends AbstractExerciseEvaluator<ErrorFindingSolution, ErrorFindingAnswer> {

    public ErrorFindingEvaluator() {
        super(ExerciseType.ERROR_FINDING, ErrorFindingSolution.class, ErrorFindingAnswer.class);
    }

    @Override
    public EvaluationResult evaluate(ErrorFindingSolution solution, ErrorFindingAnswer answer) {
        Set<Integer> expected = solution.incorrectTokenIndexes();
        Set<Integer> marked = answer.markedTokenIndexes();

        if (marked.equals(expected)) {
            return EvaluationResult.correct("Minden hibát helyesen megjelöltél.");
        }

        Set<Integer> truePositives = new HashSet<>(marked);
        truePositives.retainAll(expected);
        int falsePositives = marked.size() - truePositives.size();

        int net = Math.max(0, truePositives.size() - falsePositives);
        BigDecimal ratio = BigDecimal.valueOf(net)
                .divide(BigDecimal.valueOf(expected.size()), EvaluationResult.SCALE, java.math.RoundingMode.HALF_UP);

        return EvaluationResult.partial(ratio, "A megjelölt hibák halmaza nem pontosan egyezik.");
    }
}
