package hu.bme.szoftarch.learning.domain.model;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import tools.jackson.databind.JsonNode;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Egy párbaj-résztvevő válasza egy adott pozíción és annak pontszáma (TERV 5.5,
 * 6.2 {@code duel_answers}). A {@code (duel_set_id, user_id, position)} hármas
 * egyedi: ugyanaz a válasz ismételt beküldése nem pontoz újra (idempotencia).
 *
 * <p><strong>Kritikus (TERV 5.5):</strong> a párbaj pontja ({@code score}) NEM
 * profilpont — ez a sor sosem kezdeményez Identity pontjóváírást. A helyes
 * válasz az {@code exercise.max_points} értéket, a hibás 0-t kap.</p>
 */
@Entity
@Table(name = "duel_answers")
public class DuelAnswer {

    @EmbeddedId
    private DuelAnswerId id;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "answer", columnDefinition = "jsonb")
    private JsonNode answer;

    @Column(name = "score")
    private Integer score;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    protected DuelAnswer() {
    }

    public DuelAnswer(DuelAnswerId id, JsonNode answer, Integer score, Instant submittedAt) {
        this.id = id;
        this.answer = answer;
        this.score = score;
        this.submittedAt = submittedAt;
    }

    public DuelAnswerId getId() {
        return id;
    }

    public JsonNode getAnswer() {
        return answer;
    }

    public Integer getScore() {
        return score;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }
}
