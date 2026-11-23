package hu.bme.szoftarch.identity.application;

import hu.bme.szoftarch.identity.api.dto.AddPointsRequest;
import hu.bme.szoftarch.identity.api.dto.PointTransactionResponse;
import hu.bme.szoftarch.identity.api.dto.PublicProfileDto;
import hu.bme.szoftarch.identity.application.exception.NotFoundException;
import hu.bme.szoftarch.identity.domain.LevelCalculator;
import hu.bme.szoftarch.identity.domain.PointTransaction;
import hu.bme.szoftarch.identity.domain.User;
import hu.bme.szoftarch.identity.domain.UserStats;
import hu.bme.szoftarch.identity.infrastructure.persistence.PointTransactionRepository;
import hu.bme.szoftarch.identity.infrastructure.persistence.UserRepository;
import hu.bme.szoftarch.identity.infrastructure.persistence.UserStatsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PointService {

    private static final Logger log = LoggerFactory.getLogger(PointService.class);

    private final UserRepository userRepository;
    private final UserStatsRepository userStatsRepository;
    private final PointTransactionRepository pointTransactionRepository;

    public PointService(
            UserRepository userRepository,
            UserStatsRepository userStatsRepository,
            PointTransactionRepository pointTransactionRepository
    ) {
        this.userRepository = userRepository;
        this.userStatsRepository = userStatsRepository;
        this.pointTransactionRepository = pointTransactionRepository;
    }

    /**
     * Adds points to a user in an idempotent manner and recalculates the user's level.
     * If a transaction with the same idempotency key already exists, returns the existing
     * transaction without modifying points (duplicate = true).
     */
    @Transactional
    public PointTransactionResponse addPoints(UUID userId, AddPointsRequest request) {
        // 1. Check idempotency
        Optional<PointTransaction> existingTx = pointTransactionRepository.findByIdempotencyKey(request.idempotencyKey());
        if (existingTx.isPresent()) {
            PointTransaction tx = existingTx.get();

            log.info("Duplicate point transaction ignored: idempotencyKey={}, userId={}", request.idempotencyKey(), userId);

            UserStats stats = userStatsRepository.findById(userId)
                    .orElseThrow(() -> new NotFoundException("User stats not found for user: " + userId));

            return new PointTransactionResponse(
                    tx.getId(),
                    userId,
                    tx.getPoints(),
                    stats.getTotalPoints(),
                    stats.getLevel(),
                    LevelCalculator.calculateNextLevelPoints(stats.getLevel()),
                    true,
                    tx.getOccurredAt()
            );
        }

        // 2. Fetch user
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found: " + userId));

        // 3. Update stats & level
        UserStats stats = userStatsRepository.findById(userId)
                .orElseGet(() -> {
                    UserStats newStats = new UserStats(user);
                    return userStatsRepository.save(newStats);
                });

        long newTotal = stats.getTotalPoints() + request.points();
        int newLevel = LevelCalculator.calculateLevel(newTotal);

        stats.setTotalPoints(newTotal);
        stats.setLevel(newLevel);
        stats.setUpdatedAt(Instant.now());
        userStatsRepository.save(stats);

        // 4. Record transaction
        PointTransaction transaction = new PointTransaction(
                user,
                request.sourceType(),
                request.sourceId(),
                request.points(),
                request.idempotencyKey()
        );
        PointTransaction savedTx = pointTransactionRepository.save(transaction);

        log.info("Points added successfully: userId={}, pointsAdded={}, newTotal={}, newLevel={}, idempotencyKey={}",
                userId, request.points(), newTotal, newLevel, request.idempotencyKey());

        return new PointTransactionResponse(
                savedTx.getId(),
                userId,
                savedTx.getPoints(),
                newTotal,
                newLevel,
                LevelCalculator.calculateNextLevelPoints(newLevel),
                false,
                savedTx.getOccurredAt()
        );
    }

    /**
     * Retrieves public profile and stats for a batch of user IDs (up to 100).
     */
    @Transactional(readOnly = true)
    public List<PublicProfileDto> getBatchStats(List<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }

        List<UUID> distinctIds = userIds.stream().distinct().toList();
        List<UserStats> statsList = userStatsRepository.findAllWithUserByUserIds(distinctIds);

        return statsList.stream()
                .map(stats -> new PublicProfileDto(
                        stats.getUser().getId(),
                        stats.getUser().getUsername(),
                        stats.getUser().getDisplayName(),
                        stats.getLevel(),
                        stats.getTotalPoints()
                ))
                .toList();
    }
}
