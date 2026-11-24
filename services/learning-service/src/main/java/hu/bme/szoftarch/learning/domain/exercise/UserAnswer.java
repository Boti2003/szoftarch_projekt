package hu.bme.szoftarch.learning.domain.exercise;

/**
 * Jelölő a felhasználó deszerializált válaszaihoz az 5 automatikusan
 * kiértékelhető feladattípushoz (U3). Tiszta domain, Spring-függőség nélkül.
 *
 * <p>A válasz a kliens bemenete, NEM megbízható: az evaluator a megbízható
 * {@link ExerciseSolution}-höz méri. Rossz vagy hiányos tartalom nem dobhat
 * nyers {@link RuntimeException}-t a kliens felé — a rossz típusú válasz a
 * {@link AnswerMismatchException}-re vezet, a tartalmi hiba definiált
 * (hibás) eredményre.</p>
 */
public sealed interface UserAnswer permits
        MultipleChoiceAnswer,
        TrueFalseAnswer,
        MatchingAnswer,
        OddOneOutAnswer,
        ErrorFindingAnswer {
}
