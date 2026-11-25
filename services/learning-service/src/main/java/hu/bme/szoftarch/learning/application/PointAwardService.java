package hu.bme.szoftarch.learning.application;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import hu.bme.szoftarch.learning.domain.model.Exercise;
import hu.bme.szoftarch.learning.domain.model.ExerciseAttempt;
import hu.bme.szoftarch.learning.domain.model.PointAwardStatus;
import hu.bme.szoftarch.learning.domain.model.UserDailyProgress;
import hu.bme.szoftarch.learning.domain.model.UserDailyProgressId;
import hu.bme.szoftarch.learning.domain.port.AwardPointsCommand;
import hu.bme.szoftarch.learning.domain.port.AwardPointsResult;
import hu.bme.szoftarch.learning.domain.port.IdentityPointsPort;
import hu.bme.szoftarch.learning.domain.repository.ExerciseAttemptRepository;
import hu.bme.szoftarch.learning.domain.repository.ExerciseRepository;
import hu.bme.szoftarch.learning.domain.repository.UserDailyProgressRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A pontjóváírás elküldése az Identity felé és a saját attempt állapot
 * frissítése (TERV B – U5, 7.8). Az U4 {@code PENDING}-re állított pont-
 * szándékot az U5 küldi el a determinisztikus idempotenciakulccsal; a kimenet
 * alapján az attempt {@code AWARDED} vagy {@code FAILED_RETRYABLE} lesz.
 *
 * <p><strong>Retry-korlát (dokumentálva):</strong> újrapróbálható hiba esetén a
 * {@code next_retry_at} rendre {@code now + 1 / 5 / 15 perc} az 1., 2. és 3.
 * sikertelen próbálkozás után, és a {@code point_attempt_count} nő. A poller
 * lekérdezése csak a {@code point_attempt_count < 3} sorokat veszi fel, ezért a
 * 3. próbálkozás után a sor {@code FAILED_RETRYABLE} marad, de automatikusan
 * többé NEM kerül feldolgozásra — nincs vég nélküli próbálkozás. Nem
 * újrapróbálható (4xx) hiba esetén a {@code next_retry_at} azonnal {@code null},
 * így a sort a poller semmiképp nem választja ki.</p>
 *
 * <p><strong>Idempotencia:</strong> a retry UGYANAZZAL az idempotenciakulccsal
 * megy; a TERV 5.3 szerint ugyanaz a kulcs nem ad dupla pontot, az eredeti
 * eredményt adja vissza. Nincs elosztott tranzakció (TERV 7.8).</p>
 */
@Service
public class PointAwardService {

    private static final Logger log = LoggerFactory.getLogger(PointAwardService.class);

    /** A próbálkozásonkénti (1., 2., 3.) retry-késleltetés, perces időzítések. */
    private static final Duration[] RETRY_DELAYS = {
            Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15)
    };

    /** A napi bónusz pontértéke (TERV 5.4). */
    private static final int DAILY_BONUS_POINTS = 20;

    private final ExerciseAttemptRepository attemptRepository;
    private final ExerciseRepository exerciseRepository;
    private final UserDailyProgressRepository dailyProgressRepository;
    private final IdentityPointsPort identityPointsPort;

    public PointAwardService(ExerciseAttemptRepository attemptRepository,
            ExerciseRepository exerciseRepository, UserDailyProgressRepository dailyProgressRepository,
            IdentityPointsPort identityPointsPort) {
        this.attemptRepository = attemptRepository;
        this.exerciseRepository = exerciseRepository;
        this.dailyProgressRepository = dailyProgressRepository;
        this.identityPointsPort = identityPointsPort;
    }

    /**
     * Feldolgoz egy pont-szándékos attemptet: elküldi az Identity-nek és a
     * kimenet alapján beállítja az attempt státuszát. Saját rövid tranzakció,
     * hogy a pontküldés ne blokkolja a felhasználói választ (TERV U5).
     *
     * @param attemptId a feldolgozandó beküldés azonosítója
     */
    @Transactional
    public void processAttempt(UUID attemptId) {
        ExerciseAttempt attempt = attemptRepository.findById(attemptId).orElse(null);
        if (attempt == null || attempt.getPointIdempotencyKey() == null) {
            return;
        }
        PointAwardStatus status = attempt.getPointAwardStatus();
        if (status != PointAwardStatus.PENDING && status != PointAwardStatus.FAILED_RETRYABLE) {
            return;
        }

        Exercise exercise = exerciseRepository.findById(attempt.getExerciseId()).orElse(null);
        if (exercise == null) {
            return;
        }

        AwardPointsResult result = identityPointsPort.award(new AwardPointsCommand(
                attempt.getUserId(), "EXERCISE", attempt.getExerciseId(),
                exercise.getMaxPoints(), attempt.getPointIdempotencyKey()));

        switch (result) {
            case AWARDED -> attempt.markAwarded();
            case RETRYABLE_ERROR -> attempt.markRetryable(nextRetryAtFor(attempt.getPointAttemptCount()));
            case NON_RETRYABLE_ERROR -> attempt.markNonRetryable();
        }
        attemptRepository.save(attempt);
        log.info("Point award processed: attemptId={} outcome={}", attemptId, result);
    }

    /**
     * Feldolgoz egy napi bónuszt (TERV 5.4, 7.8): a COMPLETED-re élesített
     * {@code user_daily_progress} +20 pontját elküldi az Identity-nek az U5
     * adapteren, ugyanazzal a determinisztikus idempotenciakulccsal
     * ({@code daily:{userId}:{yyyy-MM-dd}:bonus}), és a kimenet alapján beállítja
     * a bónusz állapotát. NEM épít párhuzamos pontküldőt: ugyanaz a
     * {@link IdentityPointsPort} és ugyanaz az 1/5/15 perces, 3-ra korlátozott
     * retry-szemantika, mint az exercise-attempt pontjóváírásnál.
     *
     * <p>Az optimista zár ({@code @Version}) + az azonos kulcs garantálja, hogy a
     * +20 párhuzamosan is csak EGYSZER íródik jóvá.</p>
     *
     * @param userId      a bónusz kedvezményezettje (JWT {@code sub})
     * @param challengeId a napi kihívás azonosítója
     */
    @Transactional
    public void processDailyBonus(UUID userId, UUID challengeId) {
        UserDailyProgress progress = dailyProgressRepository
                .findById(new UserDailyProgressId(userId, challengeId)).orElse(null);
        if (progress == null || progress.getBonusIdempotencyKey() == null) {
            return;
        }
        PointAwardStatus status = progress.getBonusAwardStatus();
        if (status != PointAwardStatus.PENDING && status != PointAwardStatus.FAILED_RETRYABLE) {
            return;
        }

        AwardPointsResult result = identityPointsPort.award(new AwardPointsCommand(
                userId, "DAILY_BONUS", challengeId, DAILY_BONUS_POINTS,
                progress.getBonusIdempotencyKey()));

        switch (result) {
            case AWARDED -> progress.markBonusAwarded();
            case RETRYABLE_ERROR -> progress.markBonusRetryable(
                    nextRetryAtFor(progress.getDailyBonusAttemptCount()));
            case NON_RETRYABLE_ERROR -> progress.markBonusNonRetryable();
        }
        dailyProgressRepository.save(progress);
        log.info("Daily bonus processed: userId={} challengeId={} outcome={}",
                userId, challengeId, result);
    }

    /**
     * A következő retry időpontja az ADDIGI próbálkozás-szám alapján (a mutator
     * még nem növelte): 0 → +1 perc, 1 → +5 perc, 2 → +15 perc. A 3. próbálkozás
     * utáni hívásra (count >= 3) {@code null}, de a poller a
     * {@code point_attempt_count < 3} korláttal addigra már nem is választja ki.
     *
     * @param currentAttemptCount a próbálkozás-szám a jelen hiba ELŐTT
     * @return a következő próbálkozás ideje, vagy {@code null}, ha nincs több
     */
    private static Instant nextRetryAtFor(int currentAttemptCount) {
        if (currentAttemptCount >= RETRY_DELAYS.length) {
            return null;
        }
        return Instant.now().plus(RETRY_DELAYS[currentAttemptCount]);
    }
}
