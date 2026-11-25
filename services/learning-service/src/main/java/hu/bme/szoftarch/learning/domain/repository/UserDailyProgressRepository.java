package hu.bme.szoftarch.learning.domain.repository;

import java.time.Instant;
import java.util.List;

import hu.bme.szoftarch.learning.domain.model.UserDailyProgress;
import hu.bme.szoftarch.learning.domain.model.UserDailyProgressId;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data repository a {@link UserDailyProgress} entitáshoz (U6).
 */
public interface UserDailyProgressRepository
        extends JpaRepository<UserDailyProgress, UserDailyProgressId> {

    /**
     * Az esedékes napi bónuszokat adja vissza a pollernek (TERV 5.4, 7.8),
     * ugyanazzal a retry-szemantikával, mint az exercise-attempt pontjóváírás: a
     * még el nem küldött {@code PENDING} sorokat és azokat a
     * {@code FAILED_RETRYABLE} sorokat, amelyeknek esedékes a következő
     * próbálkozásuk. A próbálkozás-számot 3-ra korlátozzuk; a
     * {@code next_retry_at} {@code null}-ra állítása véglegesen kiveszi a sort.
     *
     * @param now   a jelen pillanat (UTC)
     * @param limit a kötegméret felső korlátja
     * @return esedékes napi haladások
     */
    @Query("""
            SELECT p FROM UserDailyProgress p
            WHERE p.bonusIdempotencyKey IS NOT NULL
              AND p.dailyBonusAttemptCount < 3
              AND (p.bonusAwardStatus = hu.bme.szoftarch.learning.domain.model.PointAwardStatus.PENDING
                   OR (p.bonusAwardStatus = hu.bme.szoftarch.learning.domain.model.PointAwardStatus.FAILED_RETRYABLE
                       AND p.nextRetryAt IS NOT NULL AND p.nextRetryAt <= :now))
            """)
    List<UserDailyProgress> findDueDailyBonuses(@Param("now") Instant now, Limit limit);
}
