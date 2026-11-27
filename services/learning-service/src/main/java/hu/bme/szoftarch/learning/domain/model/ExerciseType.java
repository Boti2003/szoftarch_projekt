package hu.bme.szoftarch.learning.domain.model;

/**
 * A támogatott feladattípusok (TERV B – Learning core).
 * A {@code TRANSLATION} és {@code AI_SCENARIO} AI-alapú, a feloldáshoz opcionális.
 */
public enum ExerciseType {
    MULTIPLE_CHOICE,
    TRUE_FALSE,
    MATCHING,
    ODD_ONE_OUT,
    ERROR_FINDING,
    TRANSLATION,
    AI_SCENARIO
}
