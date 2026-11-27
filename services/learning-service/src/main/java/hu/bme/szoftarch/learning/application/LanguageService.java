package hu.bme.szoftarch.learning.application;

import java.util.List;

import hu.bme.szoftarch.learning.api.dto.LanguageResponse;
import hu.bme.szoftarch.learning.domain.model.Language;
import hu.bme.szoftarch.learning.domain.repository.LanguageRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nyelvek lekérdezésének use case-e (TERV B – U1).
 */
@Service
public class LanguageService {

    private final LanguageRepository languageRepository;

    public LanguageService(LanguageRepository languageRepository) {
        this.languageRepository = languageRepository;
    }

    /**
     * @return az elérhető (enabled) nyelvek listája
     */
    @Transactional(readOnly = true)
    public List<LanguageResponse> listAvailableLanguages() {
        return languageRepository.findByEnabledTrueOrderByCodeAsc().stream()
                .map(LanguageService::toResponse)
                .toList();
    }

    private static LanguageResponse toResponse(Language language) {
        return new LanguageResponse(language.getId(), language.getCode(), language.getName());
    }
}
