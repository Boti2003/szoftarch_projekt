package hu.bme.szoftarch.learning.domain.exercise;

import java.util.Objects;

import hu.bme.szoftarch.learning.domain.model.Difficulty;
import hu.bme.szoftarch.learning.domain.model.ExerciseType;

/**
 * Pontszámítás segéd (U3, TERV 5.1). Tiszta domain, Spring nélkül.
 *
 * <p>Alappont típusonként; nehézségi szorzó EASY=1.0 / MEDIUM=1.5 / HARD=2.0;
 * az elérhető pont (az exercise {@code max_points} létrehozáskori értéke) az
 * {@code alappont × szorzó} lefelé kerekített értéke.</p>
 *
 * <p>A {@code TRANSLATION} és {@code AI_SCENARIO} AI-típusok pontszámát az U8
 * kezeli; ezekre a {@link #basePoints} és a {@link #maxPoints} nem
 * alkalmazható.</p>
 */
public final class ExerciseScoring {

    private ExerciseScoring() {
    }

    /**
     * A típus alappontja a TERV 5.1 tábla szerint.
     *
     * @throws NotAutoEvaluableException ha a típus AI-kiértékelésű (U8)
     */
    public static int basePoints(ExerciseType type) {
        return switch (Objects.requireNonNull(type, "type")) {
            case MULTIPLE_CHOICE -> 10;
            case TRUE_FALSE -> 8;
            case MATCHING -> 12;
            case ODD_ONE_OUT -> 10;
            case ERROR_FINDING -> 12;
            case TRANSLATION, AI_SCENARIO -> throw new NotAutoEvaluableException(type);
        };
    }

    /**
     * A nehézségi szorzó: EASY=1.0, MEDIUM=1.5, HARD=2.0.
     */
    public static double multiplier(Difficulty difficulty) {
        return switch (Objects.requireNonNull(difficulty, "difficulty")) {
            case EASY -> 1.0;
            case MEDIUM -> 1.5;
            case HARD -> 2.0;
        };
    }

    /**
     * Az elérhető pont = {@code floor(alappont × szorzó)}.
     *
     * @throws NotAutoEvaluableException ha a típus AI-kiértékelésű (U8)
     */
    public static int maxPoints(ExerciseType type, Difficulty difficulty) {
        return (int) Math.floor(basePoints(type) * multiplier(difficulty));
    }
}
