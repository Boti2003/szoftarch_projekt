package hu.bme.szoftarch.ai.infrastructure.jpa;

import hu.bme.szoftarch.ai.domain.MessageRole;
import hu.bme.szoftarch.ai.domain.ScenarioConfig;
import hu.bme.szoftarch.ai.domain.ScenarioEvaluation;
import hu.bme.szoftarch.ai.domain.ScenarioMessage;
import hu.bme.szoftarch.ai.domain.ScenarioSession;
import hu.bme.szoftarch.ai.domain.SessionStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * Oda-vissza fordítás a domain {@link ScenarioSession} aggregate és a JPA
 * entitás között. A JSONB mezőket (config, értékelés) Jacksonnel kezeli.
 */
@Component
public class ScenarioSessionMapper {

    private final ObjectMapper objectMapper;

    public ScenarioSessionMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ScenarioSessionEntity toEntity(ScenarioSession session) {
        var entity = new ScenarioSessionEntity(
                session.getId(),
                // A hívó nem ad át belső azonosítót; ezek az oszlopok nullable-ek (V2).
                null,
                null,
                null,
                session.getStatus().name(),
                session.getProvider(),
                session.getUserTurnCount(),
                writeJson(session.getConfig()),
                session.getFinalEvaluation() == null ? null : writeJson(session.getFinalEvaluation()));

        var messageEntities = session.getMessages().stream()
                .map(m -> new ScenarioMessageEntity(
                        m.id(), entity, m.sequenceNo(), m.role().name(), m.content()))
                .toList();
        entity.replaceMessages(messageEntities);
        return entity;
    }

    public ScenarioSession toDomain(ScenarioSessionEntity entity) {
        ScenarioConfig config = readJson(entity.getScenarioConfigJson(), ScenarioConfig.class);
        ScenarioEvaluation evaluation = entity.getFinalEvaluationJson() == null
                ? null
                : readJson(entity.getFinalEvaluationJson(), ScenarioEvaluation.class);

        List<ScenarioMessage> messages = entity.getMessages().stream()
                .map(m -> new ScenarioMessage(
                        m.getId(), m.getSequenceNo(), MessageRole.valueOf(m.getRole()), m.getContent()))
                .toList();

        return ScenarioSession.rehydrate(
                entity.getId(),
                entity.getProvider(),
                config,
                SessionStatus.valueOf(entity.getStatus()),
                entity.getTurnCount(),
                evaluation,
                messages);
    }

    private String writeJson(Object value) {
        return objectMapper.writeValueAsString(value);
    }

    private <T> T readJson(String json, Class<T> type) {
        return objectMapper.readValue(json, type);
    }
}
