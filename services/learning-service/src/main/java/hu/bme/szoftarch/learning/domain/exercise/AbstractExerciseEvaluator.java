package hu.bme.szoftarch.learning.domain.exercise;

import java.util.Objects;

import hu.bme.szoftarch.learning.domain.model.ExerciseType;

/**
 * Közös alap a Strategy evaluatorokhoz (U3). Elvégzi a megbízható solution és a
 * felhasználói válasz típusos ellenőrzését, hogy a konkrét implementációk már
 * típushelyes recordokkal dolgozzanak.
 *
 * <p>A solution típuseltérés {@link ExerciseContentException} (szerveroldali
 * adatintegritási hiba), a válasz típuseltérése {@link AnswerMismatchException}
 * (kezelt kliensoldali validációs hiba). Egyik üzenet sem tartalmazza a helyes
 * megoldást.</p>
 *
 * @param <S> a típushoz tartozó solution record
 * @param <A> a típushoz tartozó user answer record
 */
public abstract class AbstractExerciseEvaluator<S extends ExerciseSolution, A extends UserAnswer>
        implements ExerciseEvaluator<S, A> {

    private final ExerciseType exerciseType;
    private final Class<S> solutionType;
    private final Class<A> answerType;

    protected AbstractExerciseEvaluator(ExerciseType exerciseType, Class<S> solutionType, Class<A> answerType) {
        this.exerciseType = Objects.requireNonNull(exerciseType, "exerciseType");
        this.solutionType = Objects.requireNonNull(solutionType, "solutionType");
        this.answerType = Objects.requireNonNull(answerType, "answerType");
    }

    @Override
    public final ExerciseType exerciseType() {
        return exerciseType;
    }

    /**
     * Típus-ellenőrzött belépési pont a registry törléses (erased) hívásához.
     * A konkrét típusokra kasztol, majd a típusos {@link #evaluate} fut.
     *
     * @throws ExerciseContentException ha a solution nem a típushoz tartozó record
     * @throws AnswerMismatchException  ha a válasz nem a típushoz tartozó record
     */
    public final EvaluationResult evaluateErased(ExerciseSolution solution, UserAnswer answer) {
        Objects.requireNonNull(solution, "solution");
        Objects.requireNonNull(answer, "answer");
        if (!solutionType.isInstance(solution)) {
            throw new ExerciseContentException(
                    "Solution record does not match exercise type " + exerciseType);
        }
        if (!answerType.isInstance(answer)) {
            throw new AnswerMismatchException(
                    "Answer does not match exercise type " + exerciseType);
        }
        return evaluate(solutionType.cast(solution), answerType.cast(answer));
    }
}
