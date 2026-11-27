package hu.bme.szoftarch.social.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link DuelParticipantEntity}, keyed by the composite
 * {@link DuelParticipantId} ({@code duel_id}, {@code user_id}).
 *
 * <p>Supports loading both participants of a duel and a single participant by
 * ({@code duelId}, {@code userId}). The single-participant load is the entry
 * point for the optimistic-locked answer flow.
 */
public interface DuelParticipantRepository
        extends JpaRepository<DuelParticipantEntity, DuelParticipantId> {

    /** Loads both participant rows for a duel. */
    List<DuelParticipantEntity> findByDuelId(UUID duelId);

    /** Loads a single participant by duel and user. */
    Optional<DuelParticipantEntity> findByDuelIdAndUserId(UUID duelId, UUID userId);
}
