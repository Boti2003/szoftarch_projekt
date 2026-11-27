package hu.bme.szoftarch.learning.domain.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Egy párbaj feladatkészlete (TERV 5.5, 6.2 {@code duel_sets}). A készletet a
 * Social service kéri le a Learning belső API-ján; az {@code externalDuelId} a
 * Social párbaj UUID-ja, amelyen EGYEDI — a létrehozás idempotenciáját és a
 * versenyt a DB unique megszorítása védi. Az {@code expiresAt} opcionális (a
 * Social adhatja át), a {@code createdAt} UTC.
 */
@Entity
@Table(name = "duel_sets")
public class DuelSet {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "external_duel_id", nullable = false, unique = true)
    private UUID externalDuelId;

    @Column(name = "language_id", nullable = false)
    private UUID languageId;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DuelSet() {
    }

    public DuelSet(UUID id, UUID externalDuelId, UUID languageId, Instant expiresAt, Instant createdAt) {
        this.id = id;
        this.externalDuelId = externalDuelId;
        this.languageId = languageId;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getExternalDuelId() {
        return externalDuelId;
    }

    public UUID getLanguageId() {
        return languageId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
