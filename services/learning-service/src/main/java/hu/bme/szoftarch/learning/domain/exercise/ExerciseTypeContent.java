package hu.bme.szoftarch.learning.domain.exercise;

import java.util.Map;
import java.util.Optional;

import hu.bme.szoftarch.learning.domain.model.ExerciseType;

/**
 * Feladattípus → payload/solution record feloldás (TERV 6.2). Tiszta Java,
 * Spring-függőség nélkül: minden {@link ExerciseType}-hoz megadja, melyik
 * {@link ExercisePayload} recordra kell a {@code payload} JSONB-t
 * deszerializálni, és van-e (illetve melyik) {@link ExerciseSolution} record a
 * {@code solution} JSONB-hez.
 *
 * <p>A {@code TRANSLATION} és {@code AI_SCENARIO} AI-típusoknak nincs egzakt
 * solution recordjuk ({@code solutionType} üres).</p>
 */
public enum ExerciseTypeContent {

    MULTIPLE_CHOICE(ExerciseType.MULTIPLE_CHOICE, MultipleChoicePayload.class, MultipleChoiceSolution.class),
    TRUE_FALSE(ExerciseType.TRUE_FALSE, TrueFalsePayload.class, TrueFalseSolution.class),
    MATCHING(ExerciseType.MATCHING, MatchingPayload.class, MatchingSolution.class),
    ODD_ONE_OUT(ExerciseType.ODD_ONE_OUT, OddOneOutPayload.class, OddOneOutSolution.class),
    ERROR_FINDING(ExerciseType.ERROR_FINDING, ErrorFindingPayload.class, ErrorFindingSolution.class),
    TRANSLATION(ExerciseType.TRANSLATION, TranslationPayload.class, null),
    AI_SCENARIO(ExerciseType.AI_SCENARIO, ScenarioPayload.class, null);

    private static final Map<ExerciseType, ExerciseTypeContent> BY_TYPE;

    static {
        Map<ExerciseType, ExerciseTypeContent> byType = new java.util.EnumMap<>(ExerciseType.class);
        for (ExerciseTypeContent content : values()) {
            byType.put(content.exerciseType, content);
        }
        BY_TYPE = Map.copyOf(byType);
    }

    private final ExerciseType exerciseType;
    private final Class<? extends ExercisePayload> payloadType;
    private final Class<? extends ExerciseSolution> solutionType;

    ExerciseTypeContent(ExerciseType exerciseType,
            Class<? extends ExercisePayload> payloadType,
            Class<? extends ExerciseSolution> solutionType) {
        this.exerciseType = exerciseType;
        this.payloadType = payloadType;
        this.solutionType = solutionType;
    }

    /**
     * @return a típushoz tartozó leképezés
     */
    public static ExerciseTypeContent of(ExerciseType type) {
        ExerciseTypeContent content = BY_TYPE.get(type);
        if (content == null) {
            throw new IllegalArgumentException("No content mapping for exercise type " + type);
        }
        return content;
    }

    public Class<? extends ExercisePayload> payloadType() {
        return payloadType;
    }

    /**
     * @return a solution record típusa, vagy üres, ha a típusnak nincs egzakt
     *     megoldása (AI-típusok)
     */
    public Optional<Class<? extends ExerciseSolution>> solutionType() {
        return Optional.ofNullable(solutionType);
    }
}
