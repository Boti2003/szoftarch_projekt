package hu.bme.szoftarch.learning.domain.model;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Összetett kulcs a {@link LessonProgress} entitáshoz:
 * {@code (user_id, lesson_id)}. A {@code userId} külső domain-azonosító
 * (Identity).
 */
@Embeddable
public class LessonProgressId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "lesson_id", nullable = false)
    private UUID lessonId;

    protected LessonProgressId() {
    }

    public LessonProgressId(UUID userId, UUID lessonId) {
        this.userId = userId;
        this.lessonId = lessonId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getLessonId() {
        return lessonId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LessonProgressId that)) {
            return false;
        }
        return Objects.equals(userId, that.userId) && Objects.equals(lessonId, that.lessonId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, lessonId);
    }
}
