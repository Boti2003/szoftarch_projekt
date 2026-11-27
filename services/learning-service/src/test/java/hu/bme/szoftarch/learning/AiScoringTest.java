package hu.bme.szoftarch.learning;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import hu.bme.szoftarch.learning.domain.exercise.AiScoring;

import org.junit.jupiter.api.Test;

/**
 * Egységteszt az {@link AiScoring} küszöb- és pontlogikájára (TERV B – U8):
 * a fordítás 0.70, a párbeszéd 0.60 küszöbe és a 30-pontos korlát.
 */
class AiScoringTest {

    @Test
    void translationBelowThresholdNotAccepted() {
        assertThat(AiScoring.translationAccepted(new BigDecimal("0.69"))).isFalse();
    }

    @Test
    void translationAtThresholdAccepted() {
        assertThat(AiScoring.translationAccepted(new BigDecimal("0.70"))).isTrue();
    }

    @Test
    void scenarioAverageBelowThresholdNotCompleted() {
        BigDecimal average = AiScoring.scenarioAverage(
                new BigDecimal("0.59"), new BigDecimal("0.59"), new BigDecimal("0.59"));
        assertThat(AiScoring.scenarioCompleted(average)).isFalse();
    }

    @Test
    void scenarioAverageAtThresholdCompleted() {
        BigDecimal average = AiScoring.scenarioAverage(
                new BigDecimal("0.60"), new BigDecimal("0.60"), new BigDecimal("0.60"));
        assertThat(AiScoring.scenarioCompleted(average)).isTrue();
    }

    @Test
    void scenarioAverageIsMeanOfThreeSubScores() {
        BigDecimal average = AiScoring.scenarioAverage(
                new BigDecimal("0.30"), new BigDecimal("0.60"), new BigDecimal("0.90"));
        assertThat(average).isEqualByComparingTo(new BigDecimal("0.6000"));
    }

    @Test
    void awardedPointsRoundedToNearest() {
        int points = AiScoring.scenarioAwardedPoints(new BigDecimal("0.75"), 20);
        assertThat(points).isEqualTo(15);
    }

    @Test
    void awardedPointsCappedAtThirtyForMaxPointsThirty() {
        int points = AiScoring.scenarioAwardedPoints(BigDecimal.ONE, 30);
        assertThat(points).isEqualTo(30);
    }

    @Test
    void awardedPointsCappedAtThirtyForLargerMaxPoints() {
        int points = AiScoring.scenarioAwardedPoints(BigDecimal.ONE, 40);
        assertThat(points).isEqualTo(30);
    }
}
