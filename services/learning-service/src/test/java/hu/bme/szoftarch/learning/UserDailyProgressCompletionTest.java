package hu.bme.szoftarch.learning;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import hu.bme.szoftarch.learning.domain.model.PointAwardStatus;
import hu.bme.szoftarch.learning.domain.model.ProgressStatus;
import hu.bme.szoftarch.learning.domain.model.UserDailyProgress;
import hu.bme.szoftarch.learning.domain.model.UserDailyProgressId;

import org.junit.jupiter.api.Test;

/**
 * A napi kihívás teljesítési + bónusz-élesítési szabályának domain tesztje
 * (TERV 5.4). Spring-mentes: mindhárom feladat beküldve ÉS legalább 2 helyes →
 * COMPLETED + a bónusz egyszer élesedik; különben nem COMPLETED.
 */
class UserDailyProgressCompletionTest {

    private static UserDailyProgress newProgress() {
        return UserDailyProgress.start(new UserDailyProgressId(UUID.randomUUID(), UUID.randomUUID()));
    }

    private static final String KEY = "daily:user:2025-03-14:bonus";

    @Test
    void allSubmittedWithTwoCorrectCompletesAndArmsBonus() {
        UserDailyProgress progress = newProgress();

        progress.applyDailyResult(2, 3, KEY, Instant.now());

        assertThat(progress.getStatus()).isEqualTo(ProgressStatus.COMPLETED);
        assertThat(progress.getCompletedAt()).isNotNull();
        assertThat(progress.getBonusAwardStatus()).isEqualTo(PointAwardStatus.PENDING);
        assertThat(progress.getBonusIdempotencyKey()).isEqualTo(KEY);
        assertThat(progress.isBonusAwarded()).isFalse();
    }

    @Test
    void allSubmittedWithThreeCorrectCompletes() {
        UserDailyProgress progress = newProgress();

        progress.applyDailyResult(3, 3, KEY, Instant.now());

        assertThat(progress.getStatus()).isEqualTo(ProgressStatus.COMPLETED);
    }

    @Test
    void allSubmittedWithOneCorrectDoesNotComplete() {
        UserDailyProgress progress = newProgress();

        progress.applyDailyResult(1, 3, KEY, Instant.now());

        assertThat(progress.getStatus()).isEqualTo(ProgressStatus.IN_PROGRESS);
        assertThat(progress.getBonusAwardStatus()).isEqualTo(PointAwardStatus.NOT_APPLICABLE);
        assertThat(progress.getBonusIdempotencyKey()).isNull();
    }

    @Test
    void fewerThanThreeSubmittedDoesNotComplete() {
        UserDailyProgress progress = newProgress();

        // Két beküldés, mindkettő helyes, de nincs mind a 3 beküldve -> nem teljesül.
        progress.applyDailyResult(2, 2, KEY, Instant.now());

        assertThat(progress.getStatus()).isEqualTo(ProgressStatus.IN_PROGRESS);
        assertThat(progress.getBonusAwardStatus()).isEqualTo(PointAwardStatus.NOT_APPLICABLE);
    }

    @Test
    void bonusIsArmedOnlyOnceAcrossRepeatedCompletedTransitions() {
        UserDailyProgress progress = newProgress();

        progress.applyDailyResult(2, 3, KEY, Instant.now());
        Instant firstCompletedAt = progress.getCompletedAt();

        // Egy újabb (akár jobb) beküldés már COMPLETED állapotban: nem állítja
        // vissza, és nem élesíti újra a bónuszt.
        progress.applyDailyResult(3, 3, "daily:user:2025-03-14:bonus-other", Instant.now().plusSeconds(60));

        assertThat(progress.getCompletedAt()).isEqualTo(firstCompletedAt);
        assertThat(progress.getBonusIdempotencyKey()).isEqualTo(KEY);
        assertThat(progress.getBonusAwardStatus()).isEqualTo(PointAwardStatus.PENDING);
    }
}
