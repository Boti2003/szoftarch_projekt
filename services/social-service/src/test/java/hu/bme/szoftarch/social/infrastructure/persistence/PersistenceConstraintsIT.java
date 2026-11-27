package hu.bme.szoftarch.social.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hu.bme.szoftarch.social.domain.duel.DuelStatus;
import hu.bme.szoftarch.social.domain.duel.ParticipantStatus;
import hu.bme.szoftarch.social.domain.friend.FriendPair;
import hu.bme.szoftarch.social.domain.friend.FriendRequestStatus;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Testcontainers-backed persistence integration tests (task 4.3).
 *
 * <p>Runs the real Flyway schema (V1 baseline + V2 additive) against an ephemeral
 * PostgreSQL container so the actual database constraints and indexes are
 * exercised — not an H2 approximation. Hibernate schema generation is disabled
 * ({@code ddl-auto=none}) and the embedded-database replacement is turned off so
 * the Flyway-built schema is the one under test.
 *
 * <p>Covers:
 * <ul>
 *   <li>the ordered-pair {@code UNIQUE(user_low_id, user_high_id)} friendship
 *       constraint (Requirement 2.1),</li>
 *   <li>the partial-unique {@code uq_friend_request_pending} index on
 *       {@code (sender_id, receiver_id) WHERE status='PENDING'}, including that a
 *       non-PENDING duplicate is allowed (Requirements 10.1, 10.2), and</li>
 *   <li>that a stale {@code duel_participants.version} write surfaces an
 *       optimistic-lock failure (Requirements 10.1, 10.2).</li>
 * </ul>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Testcontainers
class PersistenceConstraintsIT {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        // Build the schema from the real migrations, not from Hibernate.
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    }

    @Autowired
    private FriendshipRepository friendshipRepository;

    @Autowired
    private FriendRequestRepository friendRequestRepository;

    @Autowired
    private DuelRepository duelRepository;

    @Autowired
    private DuelParticipantRepository duelParticipantRepository;

    @Autowired
    private EntityManager entityManager;

    private static FriendshipEntity friendship(UUID low, UUID high) {
        return new FriendshipEntity(UUID.randomUUID(), low, high, Instant.now());
    }

    private static FriendRequestEntity request(UUID sender, UUID receiver,
                                               FriendRequestStatus status) {
        Instant now = Instant.now();
        return new FriendRequestEntity(UUID.randomUUID(), sender, receiver, status, now, now);
    }

    /**
     * Orders two distinct UUIDs so {@code low < high}, satisfying the DB CHECK.
     *
     * <p>Delegates to {@link FriendPair#of} so the test and production code
     * share one ordering: the PostgreSQL {@code uuid} unsigned byte order that
     * the {@code chk_friendship_ordered} CHECK enforces. Using Java's signed
     * {@link UUID#compareTo} here would disagree with the CHECK on ~50% of
     * pairs and make this test flaky.
     */
    private static UUID[] orderedPair() {
        FriendPair pair = FriendPair.of(UUID.randomUUID(), UUID.randomUUID());
        return new UUID[] {pair.low(), pair.high()};
    }

    @Test
    void duplicateOrderedFriendshipPairViolatesUniqueConstraint() {
        UUID[] pair = orderedPair();
        friendshipRepository.saveAndFlush(friendship(pair[0], pair[1]));

        // Second row with the same ordered pair must hit UNIQUE(user_low_id, user_high_id).
        FriendshipEntity duplicate = friendship(pair[0], pair[1]);
        assertThatThrownBy(() -> friendshipRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void secondPendingRequestForSamePairViolatesPartialUniqueIndex() {
        UUID sender = UUID.randomUUID();
        UUID receiver = UUID.randomUUID();
        friendRequestRepository.saveAndFlush(
                request(sender, receiver, FriendRequestStatus.PENDING));

        FriendRequestEntity secondPending =
                request(sender, receiver, FriendRequestStatus.PENDING);
        assertThatThrownBy(() -> friendRequestRepository.saveAndFlush(secondPending))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void nonPendingDuplicateRequestIsAllowedByPartialIndex() {
        UUID sender = UUID.randomUUID();
        UUID receiver = UUID.randomUUID();

        // A rejected (non-PENDING) request plus a fresh PENDING one for the same
        // directed pair must coexist: the unique index only covers PENDING rows.
        friendRequestRepository.saveAndFlush(
                request(sender, receiver, FriendRequestStatus.REJECTED));
        friendRequestRepository.saveAndFlush(
                request(sender, receiver, FriendRequestStatus.ACCEPTED));

        assertThatCode(() -> friendRequestRepository.saveAndFlush(
                request(sender, receiver, FriendRequestStatus.PENDING)))
                .doesNotThrowAnyException();

        assertThat(friendRequestRepository.count()).isEqualTo(3);
    }

    @Test
    void staleVersionWriteSurfacesOptimisticLockFailure() {
        UUID duelId = UUID.randomUUID();
        UUID challenger = UUID.randomUUID();
        UUID opponent = UUID.randomUUID();
        Instant now = Instant.now();

        duelRepository.saveAndFlush(new DuelEntity(
                duelId, challenger, opponent, UUID.randomUUID(), null,
                DuelStatus.IN_PROGRESS, null, null, now, now));
        duelParticipantRepository.saveAndFlush(new DuelParticipantEntity(
                duelId, challenger, ParticipantStatus.IN_PROGRESS, 0, 0, now, null));

        // Clear the persistence context so the two loads below are independent,
        // detached copies that both observe version 0 (simulating two concurrent
        // request threads each holding their own snapshot).
        entityManager.flush();
        entityManager.clear();

        DuelParticipantEntity writerA =
                duelParticipantRepository.findByDuelIdAndUserId(duelId, challenger).orElseThrow();
        entityManager.detach(writerA);

        DuelParticipantEntity writerB =
                duelParticipantRepository.findByDuelIdAndUserId(duelId, challenger).orElseThrow();
        entityManager.detach(writerB);

        assertThat(writerA.getVersion()).isZero();
        assertThat(writerB.getVersion()).isZero();

        // Writer A commits first, bumping the stored version 0 -> 1.
        writerA.setScore(10);
        writerA.setAnsweredCount(1);
        duelParticipantRepository.saveAndFlush(writerA);
        entityManager.clear();

        // Writer B still believes it is at version 0; merging its stale snapshot
        // must be rejected against the now version-1 row.
        writerB.setScore(20);
        writerB.setAnsweredCount(1);
        assertThatThrownBy(() -> duelParticipantRepository.saveAndFlush(writerB))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }
}
