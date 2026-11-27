package hu.bme.szoftarch.learning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hu.bme.szoftarch.learning.domain.exercise.ExerciseScoring;
import hu.bme.szoftarch.learning.domain.exercise.NotAutoEvaluableException;
import hu.bme.szoftarch.learning.domain.model.Difficulty;
import hu.bme.szoftarch.learning.domain.model.ExerciseType;

import org.junit.jupiter.api.Test;

/**
 * A pontszámítás segéd unit tesztje (U3, TERV 5.1): alappont, szorzó és
 * {@code max_points = floor(alappont × szorzó)}.
 */
class ExerciseScoringTest {

    @Test
    void basePointsFollowTheTable() {
        assertThat(ExerciseScoring.basePoints(ExerciseType.MULTIPLE_CHOICE)).isEqualTo(10);
        assertThat(ExerciseScoring.basePoints(ExerciseType.TRUE_FALSE)).isEqualTo(8);
        assertThat(ExerciseScoring.basePoints(ExerciseType.MATCHING)).isEqualTo(12);
        assertThat(ExerciseScoring.basePoints(ExerciseType.ODD_ONE_OUT)).isEqualTo(10);
        assertThat(ExerciseScoring.basePoints(ExerciseType.ERROR_FINDING)).isEqualTo(12);
    }

    @Test
    void multipliersFollowTheTable() {
        assertThat(ExerciseScoring.multiplier(Difficulty.EASY)).isEqualTo(1.0);
        assertThat(ExerciseScoring.multiplier(Difficulty.MEDIUM)).isEqualTo(1.5);
        assertThat(ExerciseScoring.multiplier(Difficulty.HARD)).isEqualTo(2.0);
    }

    @Test
    void maxPointsFloorBasePointTimesMultiplier() {
        // TRUE_FALSE MEDIUM = floor(8 × 1.5) = 12
        assertThat(ExerciseScoring.maxPoints(ExerciseType.TRUE_FALSE, Difficulty.MEDIUM)).isEqualTo(12);
        // MATCHING HARD = floor(12 × 2.0) = 24
        assertThat(ExerciseScoring.maxPoints(ExerciseType.MATCHING, Difficulty.HARD)).isEqualTo(24);
        // MULTIPLE_CHOICE EASY = floor(10 × 1.0) = 10
        assertThat(ExerciseScoring.maxPoints(ExerciseType.MULTIPLE_CHOICE, Difficulty.EASY)).isEqualTo(10);
        // ODD_ONE_OUT MEDIUM = floor(10 × 1.5) = 15
        assertThat(ExerciseScoring.maxPoints(ExerciseType.ODD_ONE_OUT, Difficulty.MEDIUM)).isEqualTo(15);
        // ERROR_FINDING MEDIUM = floor(12 × 1.5) = 18
        assertThat(ExerciseScoring.maxPoints(ExerciseType.ERROR_FINDING, Difficulty.MEDIUM)).isEqualTo(18);
    }

    @Test
    void aiTypesHaveNoAutoScore() {
        assertThatThrownBy(() -> ExerciseScoring.basePoints(ExerciseType.TRANSLATION))
                .isInstanceOf(NotAutoEvaluableException.class);
        assertThatThrownBy(() -> ExerciseScoring.maxPoints(ExerciseType.AI_SCENARIO, Difficulty.HARD))
                .isInstanceOf(NotAutoEvaluableException.class);
    }
}
