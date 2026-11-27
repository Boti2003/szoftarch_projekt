package hu.bme.szoftarch.ai.domain;

import java.util.Optional;
import java.util.UUID;

/**
 * Perzisztencia port a szituáció-session aggregate-hez. A use case ezt
 * használja; a konkrét (JPA) megvalósítás az infrastruktúra rétegben van.
 */
public interface ScenarioSessionRepository {

    ScenarioSession save(ScenarioSession session);

    Optional<ScenarioSession> findById(UUID id);
}
