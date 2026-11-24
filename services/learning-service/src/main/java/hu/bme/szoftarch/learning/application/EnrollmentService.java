package hu.bme.szoftarch.learning.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import hu.bme.szoftarch.learning.api.NotFoundException;
import hu.bme.szoftarch.learning.api.dto.EnrollmentResponse;
import hu.bme.szoftarch.learning.api.dto.EnrollmentsResponse;
import hu.bme.szoftarch.learning.domain.model.Language;
import hu.bme.szoftarch.learning.domain.model.UserLanguageEnrollment;
import hu.bme.szoftarch.learning.domain.model.UserLanguageEnrollmentId;
import hu.bme.szoftarch.learning.domain.repository.LanguageRepository;
import hu.bme.szoftarch.learning.domain.repository.UserLanguageEnrollmentRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Felhasználói beiratkozások use case-e (TERV B – U1).
 *
 * <p>INVARIÁNS: egy felhasználónak egyszerre legfeljebb egy aktív nyelve van;
 * ezt az {@link #activateLanguage(UUID, UUID)} tartja fenn.</p>
 */
@Service
public class EnrollmentService {

    private final UserLanguageEnrollmentRepository enrollmentRepository;
    private final LanguageRepository languageRepository;

    public EnrollmentService(UserLanguageEnrollmentRepository enrollmentRepository,
            LanguageRepository languageRepository) {
        this.enrollmentRepository = enrollmentRepository;
        this.languageRepository = languageRepository;
    }

    /**
     * @return a felhasználó beiratkozásai és az aktív nyelv azonosítója
     */
    @Transactional(readOnly = true)
    public EnrollmentsResponse getEnrollments(UUID userId) {
        List<UserLanguageEnrollment> enrollments = enrollmentRepository.findByIdUserId(userId);
        Map<UUID, Language> languages = loadLanguages(enrollments);

        List<EnrollmentResponse> items = enrollments.stream()
                .map(enrollment -> toResponse(enrollment, languages.get(enrollment.getLanguageId())))
                .toList();
        UUID activeLanguageId = enrollments.stream()
                .filter(UserLanguageEnrollment::isActive)
                .map(UserLanguageEnrollment::getLanguageId)
                .findFirst()
                .orElse(null);
        return new EnrollmentsResponse(items, activeLanguageId);
    }

    /**
     * Felveszi/aktiválja a megadott nyelvet a felhasználónál: ha még nincs
     * beiratkozás, létrehozza; a kiválasztott nyelv lesz aktív, a többi
     * inaktív. Fenntartja az egy aktív nyelv invariánst.
     *
     * @throws NotFoundException ha a nyelv nem létezik vagy nem elérhető
     */
    @Transactional
    public void activateLanguage(UUID userId, UUID languageId) {
        Language language = languageRepository.findById(languageId)
                .filter(Language::isEnabled)
                .orElseThrow(() -> new NotFoundException(
                        "language_not_found", "Language not found or not available"));

        List<UserLanguageEnrollment> enrollments = new ArrayList<>(enrollmentRepository.findByIdUserId(userId));
        boolean targetExists = false;
        for (UserLanguageEnrollment enrollment : enrollments) {
            boolean isTarget = enrollment.getLanguageId().equals(language.getId());
            enrollment.setActive(isTarget);
            targetExists = targetExists || isTarget;
        }
        if (!targetExists) {
            enrollments.add(new UserLanguageEnrollment(userId, language.getId(), true, Instant.now()));
        }
        enrollmentRepository.saveAll(enrollments);
    }

    private Map<UUID, Language> loadLanguages(List<UserLanguageEnrollment> enrollments) {
        List<UUID> languageIds = enrollments.stream()
                .map(UserLanguageEnrollment::getLanguageId)
                .toList();
        return languageRepository.findAllById(languageIds).stream()
                .collect(Collectors.toMap(Language::getId, Function.identity()));
    }

    private static EnrollmentResponse toResponse(UserLanguageEnrollment enrollment, Language language) {
        return new EnrollmentResponse(
                enrollment.getLanguageId(),
                language == null ? null : language.getCode(),
                language == null ? null : language.getName(),
                enrollment.isActive(),
                enrollment.getStartedAt());
    }
}
