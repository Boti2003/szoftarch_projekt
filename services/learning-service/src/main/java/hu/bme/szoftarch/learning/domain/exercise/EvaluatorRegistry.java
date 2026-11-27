package hu.bme.szoftarch.learning.domain.exercise;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import hu.bme.szoftarch.learning.domain.model.ExerciseType;

/**
 * {@link ExerciseType} → {@link ExerciseEvaluator} feloldás az 5 automatikusan
 * kiértékelhető típusra (U3). Egyszerű, map-alapú domain konstrukció Spring
 * nélkül; a Spring-bekötés (ha kell) az application/infrastructure rétegé.
 *
 * <p>A {@code TRANSLATION} és {@code AI_SCENARIO} AI-kiértékelésű (U8), NEM
 * tartozik ide. Ezekre a registry egyértelmű „nem auto-kiértékelhető" jelzést
 * ad: a {@link #find(ExerciseType)} üres {@link Optional}-t, a
 * {@link #evaluate} pedig kezelt {@link NotAutoEvaluableException}-t — nem nyers
 * kivételt.</p>
 */
public final class EvaluatorRegistry {

    private final Map<ExerciseType, AbstractExerciseEvaluator<?, ?>> evaluators;

    private EvaluatorRegistry(Map<ExerciseType, AbstractExerciseEvaluator<?, ?>> evaluators) {
        this.evaluators = evaluators;
    }

    /**
     * @return az 5 auto típus alapértelmezett evaluatoraival feltöltött registry
     */
    public static EvaluatorRegistry withDefaults() {
        Map<ExerciseType, AbstractExerciseEvaluator<?, ?>> map = new EnumMap<>(ExerciseType.class);
        register(map, new MultipleChoiceEvaluator());
        register(map, new TrueFalseEvaluator());
        register(map, new MatchingEvaluator());
        register(map, new OddOneOutEvaluator());
        register(map, new ErrorFindingEvaluator());
        return new EvaluatorRegistry(map);
    }

    private static void register(Map<ExerciseType, AbstractExerciseEvaluator<?, ?>> map,
            AbstractExerciseEvaluator<?, ?> evaluator) {
        map.put(evaluator.exerciseType(), evaluator);
    }

    /**
     * @return igaz, ha a típust a Learning automatikusan, egzaktan értékeli
     */
    public boolean isAutoEvaluable(ExerciseType type) {
        return evaluators.containsKey(type);
    }

    /**
     * @return a típushoz tartozó evaluator, vagy üres, ha a típus nem
     *     auto-kiértékelhető (AI-típusok)
     */
    public Optional<AbstractExerciseEvaluator<?, ?>> find(ExerciseType type) {
        Objects.requireNonNull(type, "type");
        return Optional.ofNullable(evaluators.get(type));
    }

    /**
     * Kiértékeli a felhasználói választ a megadott típus megbízható
     * megoldásához mérve.
     *
     * @throws NotAutoEvaluableException ha a típus nem auto-kiértékelhető (AI)
     * @throws ExerciseContentException  ha a solution nem a típushoz tartozó record
     * @throws AnswerMismatchException   ha a válasz nem a típushoz tartozó record
     */
    public EvaluationResult evaluate(ExerciseType type, ExerciseSolution solution, UserAnswer answer) {
        AbstractExerciseEvaluator<?, ?> evaluator = evaluators.get(Objects.requireNonNull(type, "type"));
        if (evaluator == null) {
            throw new NotAutoEvaluableException(type);
        }
        return evaluator.evaluateErased(solution, answer);
    }
}
