package hu.bme.szoftarch.learning.domain.exercise;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

import hu.bme.szoftarch.learning.domain.model.ExerciseType;

/**
 * Párosítós kiértékelés (TERV 5.1): TELJES párosítás. A {@code correct} csak
 * akkor igaz, ha MINDEN pár helyes.
 *
 * <p>Részleges {@code score_ratio}: a helyes párok aránya az összes elvárt
 * párhoz képest. Egy felhasználói pár akkor helyes, ha a {@code leftId}-hez a
 * solutionbeli {@code rightId}-t rendelte. Duplikált vagy fölös párok nem
 * növelik a helyesek számát a nevező felett.</p>
 */
public final class MatchingEvaluator
        extends AbstractExerciseEvaluator<MatchingSolution, MatchingAnswer> {

    public MatchingEvaluator() {
        super(ExerciseType.MATCHING, MatchingSolution.class, MatchingAnswer.class);
    }

    @Override
    public EvaluationResult evaluate(MatchingSolution solution, MatchingAnswer answer) {
        Map<String, String> expected = new HashMap<>();
        for (MatchingSolution.Pair pair : solution.pairs()) {
            expected.put(pair.leftId(), pair.rightId());
        }

        Map<String, String> submitted = new HashMap<>();
        for (MatchingAnswer.Pair pair : answer.pairs()) {
            submitted.put(pair.leftId(), pair.rightId());
        }

        int correctPairs = 0;
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            if (entry.getValue().equals(submitted.get(entry.getKey()))) {
                correctPairs++;
            }
        }

        int total = expected.size();
        if (correctPairs == total && submitted.size() == total) {
            return EvaluationResult.correct("Minden párosítás helyes.");
        }

        BigDecimal ratio = BigDecimal.valueOf(correctPairs)
                .divide(BigDecimal.valueOf(total), EvaluationResult.SCALE, RoundingMode.HALF_UP);
        return EvaluationResult.partial(ratio, "Nem minden pár helyes.");
    }
}
