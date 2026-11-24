package hu.bme.szoftarch.learning.domain.model;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Tanulható nyelv (TERV 6.2 {@code languages} tábla). A {@code code} egyedi
 * (pl. {@code en}), a {@code enabled} jelzi, hogy a nyelv elérhető-e a
 * felhasználóknak.
 */
@Entity
@Table(name = "languages")
public class Language {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 10)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    protected Language() {
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
