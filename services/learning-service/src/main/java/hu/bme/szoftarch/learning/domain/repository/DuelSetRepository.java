package hu.bme.szoftarch.learning.domain.repository;

import java.util.Optional;
import java.util.UUID;

import hu.bme.szoftarch.learning.domain.model.DuelSet;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository a {@link DuelSet} entitáshoz (U7).
 */
public interface DuelSetRepository extends JpaRepository<DuelSet, UUID> {

    /**
     * @return a Social párbaj UUID-jához tartozó készlet, ha már létezik (a
     *     létrehozás idempotenciájához és a verseny utáni visszaolvasáshoz)
     */
    Optional<DuelSet> findByExternalDuelId(UUID externalDuelId);
}
