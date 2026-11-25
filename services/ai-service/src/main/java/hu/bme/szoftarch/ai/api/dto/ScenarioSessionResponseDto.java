package hu.bme.szoftarch.ai.api.dto;

import java.util.List;
import java.util.UUID;

/**
 * A session aktuális állapota. A záró értékelés csak lezárt session-nél
 * nem null.
 */
public record ScenarioSessionResponseDto(
        UUID sessionId,
        String status,
        int userTurnCount,
        List<MessageDto> messages,
        EvaluationDto evaluation) {

    public record MessageDto(int sequenceNo, String role, String content) {
    }

    public record EvaluationDto(
            double comprehensibility,
            double grammar,
            double taskCompletion,
            double overallScore,
            boolean passed,
            String feedback) {
    }
}
