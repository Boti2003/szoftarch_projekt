package hu.bme.szoftarch.ai.infrastructure.jpa;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Perzisztens session-állapot. Tiszta adat-entitás, üzleti logika nélkül;
 * az életciklus-szabályok a domain {@code ScenarioSession} osztályban élnek.
 * A JSONB oszlopok JSON stringként tárolódnak.
 */
@Entity
@Table(name = "ai_sessions")
public class ScenarioSessionEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "learning_attempt_id")
    private UUID learningAttemptId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "exercise_id")
    private UUID exerciseId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "provider", nullable = false)
    private String provider;

    @Column(name = "turn_count", nullable = false)
    private int turnCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scenario_config", columnDefinition = "jsonb")
    private String scenarioConfigJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "final_evaluation", columnDefinition = "jsonb")
    private String finalEvaluationJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequenceNo ASC")
    private List<ScenarioMessageEntity> messages = new ArrayList<>();

    protected ScenarioSessionEntity() {
        // JPA
    }

    public ScenarioSessionEntity(UUID id, UUID learningAttemptId, UUID userId, UUID exerciseId,
                                 String status, String provider, int turnCount,
                                 String scenarioConfigJson, String finalEvaluationJson) {
        this.id = id;
        this.learningAttemptId = learningAttemptId;
        this.userId = userId;
        this.exerciseId = exerciseId;
        this.status = status;
        this.provider = provider;
        this.turnCount = turnCount;
        this.scenarioConfigJson = scenarioConfigJson;
        this.finalEvaluationJson = finalEvaluationJson;
    }

    /** A teljes üzenetlista cseréje (mapper hívja mentés előtt). */
    public void replaceMessages(List<ScenarioMessageEntity> newMessages) {
        this.messages.clear();
        for (ScenarioMessageEntity m : newMessages) {
            m.setSession(this);
            this.messages.add(m);
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getLearningAttemptId() {
        return learningAttemptId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getExerciseId() {
        return exerciseId;
    }

    public String getStatus() {
        return status;
    }

    public String getProvider() {
        return provider;
    }

    public int getTurnCount() {
        return turnCount;
    }

    public String getScenarioConfigJson() {
        return scenarioConfigJson;
    }

    public String getFinalEvaluationJson() {
        return finalEvaluationJson;
    }

    public List<ScenarioMessageEntity> getMessages() {
        return messages;
    }
}
