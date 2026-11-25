package hu.bme.szoftarch.learning.application;

import java.time.Instant;
import java.util.List;

import hu.bme.szoftarch.learning.domain.model.ExerciseAttempt;
import hu.bme.szoftarch.learning.domain.repository.ExerciseAttemptRepository;

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
    private final PointAwardService pointAwardService;

    public PointAwardRetryScheduler(ExerciseAttemptRepository attemptRepository,
            PointAwardService pointAwardService) {
        this.attemptRepository = attemptRepository;
        this.pointAwardService = pointAwardService;
    }

    /**
     * Egy poll-ciklus: az esedékes pont-szándékokat feldolgozza. Minden attemptet
     * külön tranzakcióban, hogy egy hiba ne rontsa el a köteg többi elemét.
     */
    @Scheduled(fixedDelayString = "${learning.points.poll-delay-ms:30000}")
    public void pollDuePointAwards() {
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
}
