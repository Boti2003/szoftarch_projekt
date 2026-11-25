package hu.bme.szoftarch.learning.domain.repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import hu.bme.szoftarch.learning.domain.model.DailyChallenge;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository a {@link DailyChallenge} entitáshoz (U6).
 */
public interface DailyChallengeRepository extends JpaRepository<DailyChallenge, UUID> {

    /**
     * @return az adott üzleti napra és nyelvre vonatkozó napi kihívás, ha már
     *     létezik (a lazy létrehozás és a verseny utáni visszaolvasás alapja)
     */
    Optional<DailyChallenge> findByChallengeDateAndLanguageId(LocalDate challengeDate, UUID languageId);
}
