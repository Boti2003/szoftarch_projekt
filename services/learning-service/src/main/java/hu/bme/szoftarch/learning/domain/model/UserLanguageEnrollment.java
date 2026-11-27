package hu.bme.szoftarch.learning.domain.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Felhasználó-nyelv beiratkozás (TERV 6.2 {@code user_language_enrollments}).
 * INVARIÁNS: egy felhasználónak egyszerre legfeljebb egy {@code active} nyelve
 * lehet; ezt az aktiválás use case tartja fenn.
 */
@Entity
@Table(name = "user_language_enrollments")
public class UserLanguageEnrollment {

    @EmbeddedId
    private UserLanguageEnrollmentId id;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    protected UserLanguageEnrollment() {
    }

    public UserLanguageEnrollment(UUID userId, UUID languageId, boolean active, Instant startedAt) {
        this.id = new UserLanguageEnrollmentId(userId, languageId);
        this.active = active;
        this.startedAt = startedAt;
    }

    public UserLanguageEnrollmentId getId() {
        return id;
    }

    public UUID getUserId() {
        return id.getUserId();
    }

    public UUID getLanguageId() {
        return id.getLanguageId();
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getStartedAt() {
        return startedAt;
    }
}
