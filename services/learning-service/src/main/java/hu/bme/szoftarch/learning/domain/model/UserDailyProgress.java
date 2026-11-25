package hu.bme.szoftarch.learning.domain.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Egy felhasználó haladása egy napi kihíváson (TERV 5.4, 6.2
 * {@code user_daily_progress}). Tartalmazza a helyes válaszok számát, a
 * teljesítési állapotot és az egyszeri +20 bónusz életciklusát.
 *
 * <p><strong>Teljesítés:</strong> mindhárom feladat beküldve ÉS legalább 2
 * helyes → {@code COMPLETED}. A COMPLETED átmenetkor élesedik a bónusz
 * ({@code bonusAwardStatus=PENDING}, idempotenciakulccsal), egyetlen egyszer; a
 * tényleges +20-at az U5 {@code PointAwardService} küldi az Identity adapteren.
 * Az {@code @Version} optimista zár véd a párhuzamos bónusz-élesítés ellen.</p>
 *
 * <p>A domain logika Spring-mentes, így a teljesítés/bónusz-élesítés szabály
 * egységtesztben is ellenőrizhető.</p>
 */
@Entity
@Table(name = "user_daily_progress")
public class UserDailyProgress {

    /** A napi kihívás feladatainak száma (TERV 5.4: 3 feladat). */
    public static final int CHALLENGE_SIZE = 3;

    /** A teljesítéshez szükséges minimális helyes válasz (TERV 5.4). */
    public static final int REQUIRED_CORRECT = 2;

    @EmbeddedId
    private UserDailyProgressId id;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProgressStatus status;

    @Column(name = "correct_count", nullable = false)
    private int correctCount;

    @Column(name = "bonus_awarded", nullable = false)
    private boolean bonusAwarded;

    @Enumerated(EnumType.STRING)
    @Column(name = "bonus_award_status", nullable = false, length = 20)
    private PointAwardStatus bonusAwardStatus;

    @Column(name = "bonus_idempotency_key")
    private String bonusIdempotencyKey;

    @Column(name = "daily_bonus_attempt_count", nullable = false)
    private int dailyBonusAttemptCount;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected UserDailyProgress() {
    }

    private UserDailyProgress(UserDailyProgressId id) {
        this.id = id;
        this.status = ProgressStatus.NOT_STARTED;
        this.correctCount = 0;
        this.bonusAwarded = false;
        this.bonusAwardStatus = PointAwardStatus.NOT_APPLICABLE;
        this.dailyBonusAttemptCount = 0;
    }

    /**
     * Új, még nem kezdett napi haladás egy {@code (userId, challengeId)} párra.
     */
    public static UserDailyProgress start(UserDailyProgressId id) {
        return new UserDailyProgress(id);
    }

    /**
     * Egy napi beküldés után frissíti a haladást a TERV 5.4 teljesítési szabálya
     * szerint. A státusz legalább {@code IN_PROGRESS}; ha mindhárom feladat be
     * van küldve és legalább 2 helyes, {@code COMPLETED} lesz. A COMPLETED
     * átmenetkor — egyetlen egyszer — élesedik a bónusz
     * ({@code bonusAwardStatus=PENDING}, a megadott idempotenciakulccsal). A már
     * teljesített kihívást nem állítja vissza, és a bónuszt nem élesíti újra.
     *
     * @param correctCountAfter  a felhasználó helyes napi válaszainak száma a
     *     beküldés után
     * @param submittedCount     a felhasználó által beküldött (különböző)
     *     napi feladatok száma a beküldés után
     * @param bonusIdempotencyKey a napi bónusz determinisztikus kulcsa
     *     ({@code daily:{userId}:{yyyy-MM-dd}:bonus})
     * @param now                a beküldés időbélyege (UTC)
     */
    public void applyDailyResult(int correctCountAfter, int submittedCount, String bonusIdempotencyKey,
            Instant now) {
        this.correctCount = correctCountAfter;
        boolean completed = submittedCount >= CHALLENGE_SIZE && correctCountAfter >= REQUIRED_CORRECT;
        if (completed) {
            if (this.status != ProgressStatus.COMPLETED) {
                this.status = ProgressStatus.COMPLETED;
                this.completedAt = now;
                armBonus(bonusIdempotencyKey);
            }
        } else if (this.status != ProgressStatus.COMPLETED) {
            this.status = ProgressStatus.IN_PROGRESS;
        }
    }

    /**
     * A COMPLETED átmenetkor egyetlen egyszer élesíti a bónuszt: {@code PENDING}
     * állapot a determinisztikus kulccsal. Ha a bónusz már jóváíródott vagy
     * élesítve van, nem élesíti újra — így a +20 naponta és felhasználónként
     * egyszer jár.
     */
    private void armBonus(String bonusIdempotencyKey) {
        if (this.bonusAwarded || this.bonusAwardStatus != PointAwardStatus.NOT_APPLICABLE) {
            return;
        }
        this.bonusAwardStatus = PointAwardStatus.PENDING;
        this.bonusIdempotencyKey = bonusIdempotencyKey;
    }

    /**
     * A bónusz jóváírása sikerült: {@code AWARDED}, nincs több retry.
     */
    public void markBonusAwarded() {
        this.bonusAwarded = true;
        this.bonusAwardStatus = PointAwardStatus.AWARDED;
        this.nextRetryAt = null;
    }

    /**
     * Újrapróbálható bónusz-hiba: a próbálkozás-számot növeli,
     * {@code FAILED_RETRYABLE} állapot a következő próbálkozás idejével. A
     * {@code nextRetryAt == null} (a 3. hiba után) kiveszi a sort a poller
     * kiválasztásából — korlátozott retry.
     */
    public void markBonusRetryable(Instant nextRetryAt) {
        this.dailyBonusAttemptCount++;
        this.bonusAwardStatus = PointAwardStatus.FAILED_RETRYABLE;
        this.nextRetryAt = nextRetryAt;
    }

    /**
     * Nem újrapróbálható bónusz-hiba (4xx): {@code FAILED_RETRYABLE} állapot
     * {@code nextRetryAt == null}-lal, így a poller nem próbálkozik újra.
     */
    public void markBonusNonRetryable() {
        this.dailyBonusAttemptCount++;
        this.bonusAwardStatus = PointAwardStatus.FAILED_RETRYABLE;
        this.nextRetryAt = null;
    }

    public UserDailyProgressId getId() {
        return id;
    }

    public ProgressStatus getStatus() {
        return status;
    }

    public int getCorrectCount() {
        return correctCount;
    }

    public boolean isBonusAwarded() {
        return bonusAwarded;
    }

    public PointAwardStatus getBonusAwardStatus() {
        return bonusAwardStatus;
    }

    public String getBonusIdempotencyKey() {
        return bonusIdempotencyKey;
    }

    public int getDailyBonusAttemptCount() {
        return dailyBonusAttemptCount;
    }

    public Instant getNextRetryAt() {
        return nextRetryAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public long getVersion() {
        return version;
    }
}
