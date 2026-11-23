package hu.bme.szoftarch.identity.infrastructure.persistence;

import hu.bme.szoftarch.identity.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);
    Optional<User> findByEmailOrUsername(String email, String username);
    boolean existsByEmail(String email);
    boolean existsByUsername(String username);

    @Query("SELECT u FROM User u WHERE lower(u.username) LIKE lower(concat(:prefix, '%')) ORDER BY u.username ASC")
    List<User> searchByUsernamePrefix(@Param("prefix") String prefix);
}
