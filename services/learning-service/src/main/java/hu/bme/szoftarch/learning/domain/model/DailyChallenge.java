package hu.bme.szoftarch.learning.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Egy adott (üzleti) napra és nyelvre vonatkozó napi kihívás (TERV 5.4, 6.2
 * {@code daily_challenges}). A {@code (challengeDate, languageId)} páron egyedi
 * — a lazy létrehozás versenyét a DB unique megszorítása védi. A
 * {@code challengeDate} a budapesti üzleti nap dátuma (D17); a {@code createdAt}
 * UTC.
 */
@Entity
@Table(name = "daily_challenges")
public class DailyChallenge {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "challenge_date", nullable = false)
    private LocalDate challengeDate;

    @Column(name = "language_id", nullable = false)
    private UUID languageId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DailyChallenge() {
    }

    public DailyChallenge(UUID id, LocalDate challengeDate, UUID languageId, Instant createdAt) {
        this.id = id;
        this.challengeDate = challengeDate;
        this.languageId = languageId;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public LocalDate getChallengeDate() {
        return challengeDate;
    }

    public UUID getLanguageId() {
        return languageId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
