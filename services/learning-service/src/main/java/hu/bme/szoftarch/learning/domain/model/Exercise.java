package hu.bme.szoftarch.learning.domain.model;

import java.util.UUID;

import hu.bme.szoftarch.learning.infrastructure.persistence.JsonbNodeConverter;

import tools.jackson.databind.JsonNode;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Egy feladat egy leckében (TERV 6.2 {@code exercises} tábla). A
 * {@code payload} és {@code solution} JSONB oszlopokat nyers {@link JsonNode}
 * faként tárolja; a típusos payload/solution recordra deszerializálást a
 * feladattípus alapján az {@code ExerciseContentCodec} végzi.
 *
 * <p>U2-ben csak OLVASÁShoz használjuk (prezentáció); az attempt/írás az U4.</p>
 */
@Entity
@Table(name = "exercises")
public class Exercise {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "lesson_id", nullable = false)
    private UUID lessonId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private ExerciseType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty", nullable = false, length = 10)
    private Difficulty difficulty;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @Column(name = "prompt", nullable = false)
    private String prompt;

    @Convert(converter = JsonbNodeConverter.class)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private JsonNode payload;

    @Convert(converter = JsonbNodeConverter.class)
    @Column(name = "solution", columnDefinition = "jsonb")
    private JsonNode solution;

    @Column(name = "max_points", nullable = false)
    private int maxPoints;

    @Column(name = "required_for_completion", nullable = false)
    private boolean requiredForCompletion;

    @Column(name = "duel_eligible", nullable = false)
    private boolean duelEligible;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    protected Exercise() {
    }

    public UUID getId() {
        return id;
    }

    public UUID getLessonId() {
        return lessonId;
    }

    public ExerciseType getType() {
        return type;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public int getOrderIndex() {
        return orderIndex;
    }

    public String getPrompt() {
        return prompt;
    }

    /**
     * @return a nyers payload JSON-fa (prezentációhoz típusos recordra oldandó)
     */
    public JsonNode getPayload() {
        return payload;
    }

    /**
     * @return a nyers solution JSON-fa, vagy {@code null}; SOHA nem kerül a
     *     kliensnek szánt prezentációs válaszba (TERV 5.2)
     */
    public JsonNode getSolution() {
        return solution;
    }

    public int getMaxPoints() {
        return maxPoints;
    }

    public boolean isRequiredForCompletion() {
        return requiredForCompletion;
    }

    public boolean isDuelEligible() {
        return duelEligible;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
