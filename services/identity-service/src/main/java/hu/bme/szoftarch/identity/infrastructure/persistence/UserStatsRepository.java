package hu.bme.szoftarch.identity.infrastructure.persistence;

import hu.bme.szoftarch.identity.domain.UserStats;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface UserStatsRepository extends JpaRepository<UserStats, UUID> {
    @Query("SELECT s FROM UserStats s JOIN FETCH s.user ORDER BY s.totalPoints DESC, s.updatedAt ASC")
    Page<UserStats> findLeaderboard(Pageable pageable);

    @Query("SELECT COUNT(s) + 1 FROM UserStats s WHERE s.totalPoints > :points")
    long calculateRank(@Param("points") long points);

    @Query("SELECT s FROM UserStats s JOIN FETCH s.user WHERE s.userId IN :userIds")
    List<UserStats> findAllWithUserByUserIds(@Param("userIds") List<UUID> userIds);
}
