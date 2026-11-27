package hu.bme.szoftarch.learning.domain.exercise;

import hu.bme.szoftarch.learning.domain.model.ExerciseType;

/**
 * Igaz-hamis kiértékelés (TERV 5.1): a feleletválasztós speciális esete — a
 * felhasználó igaz/hamis értéke egyezik a solutionnel. Egzakt.
 */
public final class TrueFalseEvaluator
        extends AbstractExerciseEvaluator<TrueFalseSolution, TrueFalseAnswer> {

    public TrueFalseEvaluator() {
        super(ExerciseType.TRUE_FALSE, TrueFalseSolution.class, TrueFalseAnswer.class);
    }

    @Override
    public EvaluationResult evaluate(TrueFalseSolution solution, TrueFalseAnswer answer) {
        boolean correct = answer.answer() == solution.answer();
        return correct
                ? EvaluationResult.correct("Helyes válasz.")
                : EvaluationResult.incorrect("Helytelen válasz.");
    }
}
