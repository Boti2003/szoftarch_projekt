package hu.bme.szoftarch.learning.domain.exercise;

import hu.bme.szoftarch.learning.domain.model.ExerciseType;

/**
 * Strategy minta (TERV 0.) egy automatikusan kiértékelhető feladattípus egzakt
 * kiértékeléséhez (U3). Tiszta domain, Spring-függőség nélkül és így önmagában
 * unit-tesztelhető.
 *
 * <p>A bemenet a megbízható {@link ExerciseSolution} record és a felhasználó
 * deszerializált {@link UserAnswer}-je; a kimenet egy {@link EvaluationResult}.
 * Rossz típusú válasz esetén {@link AnswerMismatchException}; rossz/hiányos
 * tartalom esetén definiált (hibás) eredmény — nyers {@link RuntimeException}
 * nem szivároghat a kliens felé, és a megoldás sem.</p>
 *
 * @param <S> a típushoz tartozó solution record
 * @param <A> a típushoz tartozó user answer record
 */
public interface ExerciseEvaluator<S extends ExerciseSolution, A extends UserAnswer> {

    /**
     * @return a típus, amelyet ez a stratégia kiértékel
     */
    ExerciseType exerciseType();

    /**
     * Kiértékeli a felhasználói választ a megbízható megoldáshoz mérve.
     *
     * @param solution a megbízható megoldás record (nem {@code null})
     * @param answer   a felhasználó válasza (nem {@code null})
     * @return a kiértékelés eredménye
     * @throws AnswerMismatchException ha a válasz nem a típushoz tartozó record
     */
    EvaluationResult evaluate(S solution, A answer);
}
