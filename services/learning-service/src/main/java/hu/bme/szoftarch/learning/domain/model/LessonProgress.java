package hu.bme.szoftarch.learning.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Egy felhasználó haladása egy leckén (TERV 6.2 {@code lesson_progress}).
 * U1-ben csak OLVASÁShoz használjuk a leckelista státuszához és
 * elérhetőségéhez; az írást (attempt, completion) az U4 végzi az optimista
 * zár ({@link #version}) felhasználásával.
 */
@Entity
@Table(name = "lesson_progress")
public class LessonProgress {

    @EmbeddedId
    private LessonProgressId id;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProgressStatus status;

    @Column(name = "best_score_ratio")
    private BigDecimal bestScoreRatio;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected LessonProgress() {
    }

    private LessonProgress(LessonProgressId id, ProgressStatus status) {
        this.id = id;
        this.status = status;
    }

    /**
     * Új, még nem kezdett haladás létrehozása egy (userId, lessonId) párra.
     */
    public static LessonProgress start(LessonProgressId id) {
        return new LessonProgress(id, ProgressStatus.NOT_STARTED);
    }

    /**
     * A súlyozott legjobb pontarányt és a teljesítési állapotot frissíti egy
     * beküldés után (TERV 5.3). A státusz legalább {@code IN_PROGRESS}; ha a
     * lecke teljesült, {@code COMPLETED} lesz a {@code completedAt} beállításával
     * (egyszer, a már teljesített leckét nem állítja vissza).
     *
     * @param weightedBestRatio a kötelező feladatok súlyozott legjobb pontaránya
     * @param completed         igaz, ha a lecke teljesítési feltétele teljesült
     * @param now               a teljesítés időbélyege
     */
    public void applySubmission(BigDecimal weightedBestRatio, boolean completed, Instant now) {
        this.bestScoreRatio = weightedBestRatio;
        if (completed) {
            if (this.status != ProgressStatus.COMPLETED) {
                this.status = ProgressStatus.COMPLETED;
                this.completedAt = now;
            }
        } else if (this.status != ProgressStatus.COMPLETED) {
            this.status = ProgressStatus.IN_PROGRESS;
        }
    }

    public LessonProgressId getId() {
        return id;
    }

    public ProgressStatus getStatus() {
        return status;
    }

    public BigDecimal getBestScoreRatio() {
        return bestScoreRatio;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public long getVersion() {
        return version;
    }
}
