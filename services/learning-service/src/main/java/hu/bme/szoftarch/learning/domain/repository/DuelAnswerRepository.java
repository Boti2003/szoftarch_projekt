package hu.bme.szoftarch.learning.domain.repository;

import hu.bme.szoftarch.learning.domain.model.DuelAnswer;
import hu.bme.szoftarch.learning.domain.model.DuelAnswerId;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository a {@link DuelAnswer} entitáshoz (U7). A tárolt válasz
 * az összetett {@link DuelAnswerId} ({@code duel_set_id, user_id, position})
 * alapján kérhető vissza (öröklött {@code findById}) a pontozás
 * idempotenciájához.
 */
public interface DuelAnswerRepository extends JpaRepository<DuelAnswer, DuelAnswerId> {
}
