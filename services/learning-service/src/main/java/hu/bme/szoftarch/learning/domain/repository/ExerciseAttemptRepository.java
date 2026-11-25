package hu.bme.szoftarch.learning.domain.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

import hu.bme.szoftarch.learning.domain.model.ExerciseAttempt;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository a {@link ExerciseAttempt} entitáshoz (U4).
 */
public interface ExerciseAttemptRepository extends JpaRepository<ExerciseAttempt, UUID> {

    /**
     * @return a megadott idempotencia-kulcshoz tartozó beküldés, ha már létezik
     */
    Optional<ExerciseAttempt> findByIdempotencyKey(String idempotencyKey);

    /**
     * @return a felhasználó összes beküldése az adott feladatra (első-helyes
     *     detektáláshoz és a feladatonkénti legjobb pontarányhoz)
     */
    List<ExerciseAttempt> findByUserIdAndExerciseId(UUID userId, UUID exerciseId);

    /**
     * @return a felhasználó beküldései a megadott feladatokra (a lecke legjobb
     *     pontarányainak aggregálásához)
     */
    List<ExerciseAttempt> findByUserIdAndExerciseIdIn(UUID userId, Collection<UUID> exerciseIds);

    /**
     * Az esedékes pontjóváírásokat adja vissza a pollernek (TERV 7.8): a még el
     * nem küldött {@code PENDING} sorokat és azokat a {@code FAILED_RETRYABLE}
     * sorokat, amelyeknek esedékes a következő próbálkozásuk. A próbálkozás-
     * számot 3-ra korlátozzuk (1/5/15 perc időzítés); a {@code next_retry_at}
     * {@code null}-ra állítása (3. hiba után, illetve 4xx esetén) véglegesen
     * kiveszi a sort a kiválasztásból.
     *
     * @param now   a jelen pillanat (UTC)
     * @param limit a kötegméret felső korlátja
     * @return esedékes beküldések beküldési idő szerint növekvő sorrendben
     */
    @Query("""
            SELECT a FROM ExerciseAttempt a
            WHERE a.pointIdempotencyKey IS NOT NULL
              AND a.pointAttemptCount < 3
              AND (a.pointAwardStatus = hu.bme.szoftarch.learning.domain.model.PointAwardStatus.PENDING
                   OR (a.pointAwardStatus = hu.bme.szoftarch.learning.domain.model.PointAwardStatus.FAILED_RETRYABLE
                       AND a.nextRetryAt IS NOT NULL AND a.nextRetryAt <= :now))
            ORDER BY a.submittedAt ASC
            """)
    List<ExerciseAttempt> findDuePointAwards(@Param("now") Instant now, Limit limit);
}
