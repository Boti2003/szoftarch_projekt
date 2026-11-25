package hu.bme.szoftarch.learning.domain.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import hu.bme.szoftarch.learning.domain.model.ExerciseAttempt;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository a {@link ExerciseAttempt} entitáshoz (U4).
 */
public interface ExerciseAttemptRepository extends JpaRepository<ExerciseAttempt, UUID> {

    /**
     * @return a megadott idempotencia-kulcshoz tartozó beküldés, ha már létezik
     */
    Optional<ExerciseAttempt> findByIdempotencyKey(String idempotencyKey);

    /**
     * @return a felhasználó összes beküldése az adott feladatra (első-helyes
     *     detektáláshoz és a feladatonkénti legjobb pontarányhoz)
     */
    List<ExerciseAttempt> findByUserIdAndExerciseId(UUID userId, UUID exerciseId);

    /**
     * @return a felhasználó beküldései a megadott feladatokra (a lecke legjobb
     *     pontarányainak aggregálásához)
     */
    List<ExerciseAttempt> findByUserIdAndExerciseIdIn(UUID userId, Collection<UUID> exerciseIds);
}
