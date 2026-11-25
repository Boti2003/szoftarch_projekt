package hu.bme.szoftarch.ai.infrastructure.jpa;

import hu.bme.szoftarch.ai.domain.ScenarioSession;
import hu.bme.szoftarch.ai.domain.ScenarioSessionRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * A domain {@link ScenarioSessionRepository} port JPA-megvalósítása.
 * A domain és a perzisztens entitás között a {@link ScenarioSessionMapper}
 * fordít.
 */
@Repository
public class JpaScenarioSessionRepository implements ScenarioSessionRepository {

    private final ScenarioSessionJpaRepository jpaRepository;
    private final ScenarioSessionMapper mapper;

    public JpaScenarioSessionRepository(ScenarioSessionJpaRepository jpaRepository,
                                        ScenarioSessionMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public ScenarioSession save(ScenarioSession session) {
        ScenarioSessionEntity saved = jpaRepository.save(mapper.toEntity(session));
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<ScenarioSession> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public boolean existsByLearningAttemptId(UUID learningAttemptId) {
        return jpaRepository.existsByLearningAttemptId(learningAttemptId);
    }
}
