package hu.bme.szoftarch.ai.infrastructure.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Perzisztens beszélgetési üzenet. Tiszta adat-entitás, üzleti logika nélkül.
 */
@Entity
@Table(name = "ai_messages")
public class ScenarioMessageEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "session_id", nullable = false)
    private ScenarioSessionEntity session;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    @Column(name = "role", nullable = false)
    private String role;

    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected ScenarioMessageEntity() {
        // JPA
    }

    public ScenarioMessageEntity(UUID id, ScenarioSessionEntity session, int sequenceNo,
                                 String role, String content) {
        this.id = id;
        this.session = session;
        this.sequenceNo = sequenceNo;
        this.role = role;
        this.content = content;
    }

    public UUID getId() {
        return id;
    }

    public int getSequenceNo() {
        return sequenceNo;
    }

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    void setSession(ScenarioSessionEntity session) {
        this.session = session;
    }
}
