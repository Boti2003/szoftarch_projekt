package hu.bme.szoftarch.identity.api;

import hu.bme.szoftarch.identity.api.dto.AddPointsRequest;
import hu.bme.szoftarch.identity.api.dto.BatchStatsRequest;
import hu.bme.szoftarch.identity.api.dto.PointTransactionResponse;
import hu.bme.szoftarch.identity.api.dto.PublicProfileDto;
import hu.bme.szoftarch.identity.application.PointService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/users")
public class InternalUserController {

    private final PointService pointService;

    public InternalUserController(PointService pointService) {
        this.pointService = pointService;
    }

    /**
     * Adds points to a user idempotently and recalculates the user's level.
     */
    @PostMapping("/{userId}/points")
    public ResponseEntity<PointTransactionResponse> addPoints(
            @PathVariable("userId") UUID userId,
            @Valid @RequestBody AddPointsRequest request
    ) {
        return ResponseEntity.ok(pointService.addPoints(userId, request));
    }

    /**
     * Batch retrieves public profile and game stats for multiple users.
     */
    @PostMapping("/public-stats:batch")
    public ResponseEntity<List<PublicProfileDto>> getBatchStats(
            @Valid @RequestBody BatchStatsRequest request
    ) {
        return ResponseEntity.ok(pointService.getBatchStats(request.userIds()));
    }
}
