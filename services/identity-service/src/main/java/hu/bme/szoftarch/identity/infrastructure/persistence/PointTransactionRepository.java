package hu.bme.szoftarch.identity.infrastructure.persistence;

import hu.bme.szoftarch.identity.domain.PointTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PointTransactionRepository extends JpaRepository<PointTransaction, UUID> {
    Optional<PointTransaction> findByIdempotencyKey(String idempotencyKey);
}
