package hu.bme.szoftarch.social.infrastructure.persistence;

import hu.bme.szoftarch.social.domain.duel.DuelStatus;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data repository for {@link DuelEntity}.
 *
 * <p>Supports paged listing of the duels a user takes part in (as challenger or
 * opponent), with an optional status filter.
 */
public interface DuelRepository extends JpaRepository<DuelEntity, UUID> {

    /**
     * Pages the duels a user participates in (challenger or opponent). When
     * {@code status} is {@code null} no status filter is applied.
     */
    @Query("""
            SELECT d FROM DuelEntity d
            WHERE (d.challengerId = :userId OR d.opponentId = :userId)
              AND (:status IS NULL OR d.status = :status)
            """)
    Page<DuelEntity> findForUser(
            @Param("userId") UUID userId,
            @Param("status") DuelStatus status,
            Pageable pageable);
}
