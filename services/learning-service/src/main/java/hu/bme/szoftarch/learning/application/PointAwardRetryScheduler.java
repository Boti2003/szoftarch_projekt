package hu.bme.szoftarch.learning.application;

import java.time.Instant;
import java.util.List;

import hu.bme.szoftarch.learning.domain.model.ExerciseAttempt;
import hu.bme.szoftarch.learning.domain.model.UserDailyProgress;
import hu.bme.szoftarch.learning.domain.model.UserDailyProgressId;
import hu.bme.szoftarch.learning.domain.repository.ExerciseAttemptRepository;
import hu.bme.szoftarch.learning.domain.repository.UserDailyProgressRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Ütemezett poller a pontjóváírás életciklusához (TERV B – U5, 7.8). Felveszi az
 * esedékes {@code PENDING} (első küldés) és {@code FAILED_RETRYABLE}
 * ({@code next_retry_at <= now}) beküldéseket, és soronként a
 * {@link PointAwardService}-re bízza az Identity-hívást + állapotátmenetet.
 *
 * <p>A retry UGYANAZZAL az idempotenciakulccsal megy, és a próbálkozások száma
 * 3-ra korlátozott (1/5/15 perc). Tesztben kikapcsolható
 * ({@code learning.points.scheduler-enabled=false}), hogy a tesztek a poller
 * metódust determinisztikusan, közvetlenül hívhassák.</p>
 */
@Component
@ConditionalOnProperty(name = "learning.points.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class PointAwardRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(PointAwardRetryScheduler.class);
    private static final Limit BATCH_LIMIT = Limit.of(100);

    private final ExerciseAttemptRepository attemptRepository;
    private final UserDailyProgressRepository dailyProgressRepository;
    private final PointAwardService pointAwardService;

    public PointAwardRetryScheduler(ExerciseAttemptRepository attemptRepository,
            UserDailyProgressRepository dailyProgressRepository,
            PointAwardService pointAwardService) {
        this.attemptRepository = attemptRepository;
        this.dailyProgressRepository = dailyProgressRepository;
        this.pointAwardService = pointAwardService;
    }

    /**
     * Egy poll-ciklus: az esedékes pont-szándékokat és napi bónuszokat
     * feldolgozza. Minden elemet külön tranzakcióban, hogy egy hiba ne rontsa el
     * a köteg többi elemét.
     */
    @Scheduled(fixedDelayString = "${learning.points.poll-delay-ms:30000}")
    public void pollDuePointAwards() {
        pollDueExerciseAttempts();
        pollDueDailyBonuses();
    }

    /**
     * Az esedékes exercise-attempt pontjóváírások feldolgozása.
     */
    private void pollDueExerciseAttempts() {
        List<ExerciseAttempt> due = attemptRepository.findDuePointAwards(Instant.now(), BATCH_LIMIT);
        if (due.isEmpty()) {
            return;
        }
        log.info("Point award poll: {} due attempt(s)", due.size());
        for (ExerciseAttempt attempt : due) {
            try {
                pointAwardService.processAttempt(attempt.getId());
            } catch (RuntimeException e) {
                // Egy sor hibája ne állítsa meg a kötegetet; a következő pollnál újra próbáljuk.
                log.warn("Point award processing error for attemptId={}: {}",
                        attempt.getId(), e.getClass().getSimpleName());
            }
        }
    }

    /**
     * Az esedékes napi bónuszok feldolgozása (TERV 5.4): ugyanaz a poller, ugyanaz
     * a retry-szemantika; a +20 az U5 {@link PointAwardService}-en megy.
     */
    private void pollDueDailyBonuses() {
        List<UserDailyProgress> due = dailyProgressRepository.findDueDailyBonuses(Instant.now(), BATCH_LIMIT);
        if (due.isEmpty()) {
            return;
        }
        log.info("Daily bonus poll: {} due progress row(s)", due.size());
        for (UserDailyProgress progress : due) {
            UserDailyProgressId id = progress.getId();
            try {
                pointAwardService.processDailyBonus(id.getUserId(), id.getChallengeId());
            } catch (RuntimeException e) {
                log.warn("Daily bonus processing error for userId={} challengeId={}: {}",
                        id.getUserId(), id.getChallengeId(), e.getClass().getSimpleName());
            }
        }
    }
}
