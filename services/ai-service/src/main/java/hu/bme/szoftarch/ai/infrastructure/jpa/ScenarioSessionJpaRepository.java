package hu.bme.szoftarch.ai.infrastructure.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Spring Data JPA repository a session-entitáshoz.
 */
public interface ScenarioSessionJpaRepository extends JpaRepository<ScenarioSessionEntity, UUID> {

    boolean existsByLearningAttemptId(UUID learningAttemptId);
}
