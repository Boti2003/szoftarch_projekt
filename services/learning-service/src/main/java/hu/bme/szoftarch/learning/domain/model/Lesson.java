package hu.bme.szoftarch.learning.domain.model;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Lecke egy nyelven belül (TERV 6.2 {@code lessons} tábla). A sorrendet az
 * {@code orderIndex} adja; a {@code languageId} külső FK a {@code languages}
 * táblára.
 */
@Entity
@Table(name = "lessons")
public class Lesson {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "language_id", nullable = false)
    private UUID languageId;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "theory_markdown")
    private String theoryMarkdown;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    protected Lesson() {
    }

    public UUID getId() {
        return id;
    }

    public UUID getLanguageId() {
        return languageId;
    }

    public int getOrderIndex() {
        return orderIndex;
    }

    public String getTitle() {
        return title;
    }

    public String getTheoryMarkdown() {
        return theoryMarkdown;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
