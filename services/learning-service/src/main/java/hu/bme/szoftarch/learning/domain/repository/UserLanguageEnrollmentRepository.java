package hu.bme.szoftarch.learning.domain.repository;

import java.util.List;
import java.util.UUID;

import hu.bme.szoftarch.learning.domain.model.UserLanguageEnrollment;
import hu.bme.szoftarch.learning.domain.model.UserLanguageEnrollmentId;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository a {@link UserLanguageEnrollment} entitáshoz. Minden
 * lekérdezés a hitelesített {@code userId}-re szűr (a kliens sosem kap más
 * felhasználó adatát).
 */
public interface UserLanguageEnrollmentRepository
        extends JpaRepository<UserLanguageEnrollment, UserLanguageEnrollmentId> {

    List<UserLanguageEnrollment> findByIdUserId(UUID userId);
}
