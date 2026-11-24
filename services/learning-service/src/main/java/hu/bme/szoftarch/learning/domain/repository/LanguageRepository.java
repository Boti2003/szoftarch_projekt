package hu.bme.szoftarch.learning.domain.repository;

import java.util.List;
import java.util.UUID;

import hu.bme.szoftarch.learning.domain.model.Language;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository a {@link Language} entitáshoz.
 */
public interface LanguageRepository extends JpaRepository<Language, UUID> {

    /**
     * @return az elérhető (enabled) nyelvek {@code code} szerint rendezve
     */
    List<Language> findByEnabledTrueOrderByCodeAsc();
}
