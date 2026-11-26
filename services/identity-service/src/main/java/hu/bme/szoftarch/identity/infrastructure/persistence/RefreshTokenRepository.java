package hu.bme.szoftarch.identity.infrastructure.persistence;

import hu.bme.szoftarch.identity.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;


public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    
    /**
     * Revokes all active refresh tokens for a user (sets revoked_at = now).
     * Used upon password change or account compromise to terminate all other active device sessions.
     *
     * IMPORTANT PERSISTENCE BEHAVIOR:
     * 1. 'flushAutomatically = true': Mandatory so any pending modifications in the current persistence
     *    context (e.g. user.setPasswordHash() or saveAndFlush) are flushed to the DB before the bulk UPDATE
     *    executes. Without this, pending entity changes could be wiped when the persistence context clears.
     * 2. 'clearAutomatically = true': Evicts stale RefreshToken snapshots from Hibernate's first-level (L1)
     *    cache. This guarantees that subsequent reads in the same transaction fetch the updated 'revoked_at'
     *    state directly from PostgreSQL.
     *
     *
     * This immediately prevents any other device from refreshing tokens or re-establishing 
     * sessions upon page reload. Existing in-memory access tokens
     * on other devices remain mathematically valid until their TTL naturally expires.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE RefreshToken r SET r.revokedAt = :now WHERE r.user.id = :userId AND r.revokedAt IS NULL")
    int revokeAllForUser(@Param("userId") UUID userId, @Param("now") Instant now);
}
