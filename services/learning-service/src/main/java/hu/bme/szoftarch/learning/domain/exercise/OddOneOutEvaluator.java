package hu.bme.szoftarch.learning.domain.exercise;

import hu.bme.szoftarch.learning.domain.model.ExerciseType;

/**
 * Kakukktojás kiértékelés (TERV 5.1): a kiválasztott elem ID-ja egyezik a
 * kilógó elem helyes ID-jával. Egzakt; üres (null) válasz hibás eredmény.
 */
public final class OddOneOutEvaluator
        extends AbstractExerciseEvaluator<OddOneOutSolution, OddOneOutAnswer> {

    public OddOneOutEvaluator() {
        super(ExerciseType.ODD_ONE_OUT, OddOneOutSolution.class, OddOneOutAnswer.class);
    }

    @Override
    public EvaluationResult evaluate(OddOneOutSolution solution, OddOneOutAnswer answer) {
        boolean correct = solution.oddItemId().equals(answer.selectedItemId());
        return correct
                ? EvaluationResult.correct("Helyes válasz.")
                : EvaluationResult.incorrect("A kiválasztott elem nem a kilógó.");
    }
}
