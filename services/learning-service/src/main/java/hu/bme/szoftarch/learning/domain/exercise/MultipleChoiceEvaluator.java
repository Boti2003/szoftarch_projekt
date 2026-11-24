package hu.bme.szoftarch.learning.domain.exercise;

import hu.bme.szoftarch.learning.domain.model.ExerciseType;

/**
 * Feleletválasztós kiértékelés (TERV 5.1): a kiválasztott opció-ID-k halmaza
 * pontosan egyezik a helyes ID-k halmazával. Egyszeri és többszörös helyes
 * válasz is a halmaz-egyezésből adódik. Egzakt: részleges pont nincs.
 */
public final class MultipleChoiceEvaluator
        extends AbstractExerciseEvaluator<MultipleChoiceSolution, MultipleChoiceAnswer> {

    public MultipleChoiceEvaluator() {
        super(ExerciseType.MULTIPLE_CHOICE, MultipleChoiceSolution.class, MultipleChoiceAnswer.class);
    }

    @Override
    public EvaluationResult evaluate(MultipleChoiceSolution solution, MultipleChoiceAnswer answer) {
        boolean correct = answer.selectedOptionIds().equals(solution.correctOptionIds());
        return correct
                ? EvaluationResult.correct("Helyes válasz.")
                : EvaluationResult.incorrect("A kiválasztott opciók nem pontosan a helyesek.");
    }
}
