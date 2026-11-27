package hu.bme.szoftarch.learning.domain.exercise;

import hu.bme.szoftarch.learning.domain.model.ExerciseType;

/**
 * A feladattípus nem a Learning automatikus (egzakt) kiértékelésébe tartozik:
 * {@code TRANSLATION} és {@code AI_SCENARIO} AI-kiértékelésű (U8). Kezelt,
 * egyértelmű jelzés — nem nyers {@link RuntimeException} —, hogy a hívó az
 * AI-ágra irányíthasson.
 */
public class NotAutoEvaluableException extends RuntimeException {

    private final ExerciseType exerciseType;

    public NotAutoEvaluableException(ExerciseType exerciseType) {
        super("Exercise type " + exerciseType + " is not auto-evaluable (AI-evaluated)");
        this.exerciseType = exerciseType;
    }

    public ExerciseType exerciseType() {
        return exerciseType;
    }
}
