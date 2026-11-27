package hu.bme.szoftarch.learning.domain.model;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Összetett kulcs a {@link UserLanguageEnrollment} entitáshoz:
 * {@code (user_id, language_id)}. A {@code userId} külső domain-azonosító
 * (Identity), nincs FK más service táblájára.
 */
@Embeddable
public class UserLanguageEnrollmentId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "language_id", nullable = false)
    private UUID languageId;

    protected UserLanguageEnrollmentId() {
    }

    public UserLanguageEnrollmentId(UUID userId, UUID languageId) {
        this.userId = userId;
        this.languageId = languageId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getLanguageId() {
        return languageId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UserLanguageEnrollmentId that)) {
            return false;
        }
        return Objects.equals(userId, that.userId) && Objects.equals(languageId, that.languageId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, languageId);
    }
}
