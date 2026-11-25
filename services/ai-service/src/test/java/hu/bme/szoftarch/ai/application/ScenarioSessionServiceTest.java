package hu.bme.szoftarch.ai.application;

import hu.bme.szoftarch.ai.domain.ScenarioConfig;
import hu.bme.szoftarch.ai.domain.ScenarioSession;
import hu.bme.szoftarch.ai.domain.ScenarioSessionRepository;
import hu.bme.szoftarch.ai.domain.SessionStatus;
import hu.bme.szoftarch.ai.infrastructure.FakeLanguageModelClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScenarioSessionServiceTest {

    private ScenarioSessionService service;

    @BeforeEach
    void setUp() {
        service = new ScenarioSessionService(new InMemoryRepository(), new FakeLanguageModelClient(), "fake");
    }

    private ScenarioConfig config() {
        return new ScenarioConfig("waiter", "restaurant", "order food drink", "en", "Welcome!");
    }

    @Test
    void startCreatesOpenSessionWithOpeningMessage() {
        var session = service.start(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), config());

        assertThat(session.getStatus()).isEqualTo(SessionStatus.OPEN);
        assertThat(session.getMessages()).hasSize(1);
    }

    @Test
    void duplicateLearningAttemptRejected() {
        UUID attemptId = UUID.randomUUID();
        service.start(attemptId, UUID.randomUUID(), UUID.randomUUID(), config());

        assertThatThrownBy(() ->
                service.start(attemptId, UUID.randomUUID(), UUID.randomUUID(), config()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void eachUserMessageGetsAssistantReply() {
        var started = service.start(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), config());

        var updated = service.addUserMessage(started.getId(), "I want a table please");

        assertThat(updated.getUserTurnCount()).isEqualTo(1);
        // opening + user + assistant reply
        assertThat(updated.getMessages()).hasSize(3);
    }

    @Test
    void autoCompletesAtMaxTurns() {
        var started = service.start(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), config());
        UUID id = started.getId();

        ScenarioSession last = null;
        for (int i = 0; i < ScenarioSession.MAX_USER_TURNS; i++) {
            last = service.addUserMessage(id, "message number " + i + " with some words");
        }

        assertThat(last).isNotNull();
        assertThat(last.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(last.getFinalEvaluation()).isNotNull();
    }

    @Test
    void manualCompleteProducesEvaluation() {
        var started = service.start(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), config());
        UUID id = started.getId();
        service.addUserMessage(id, "one message here");
        service.addUserMessage(id, "two message here");
        service.addUserMessage(id, "three message here");

        var completed = service.complete(id);

        assertThat(completed.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(completed.getFinalEvaluation()).isNotNull();
    }

    /** Egyszerű in-memory repository a use case teszteléséhez, DB nélkül. */
    private static class InMemoryRepository implements ScenarioSessionRepository {
        private final Map<UUID, ScenarioSession> store = new HashMap<>();
        private final Map<UUID, UUID> byAttempt = new HashMap<>();

        @Override
        public ScenarioSession save(ScenarioSession session) {
            store.put(session.getId(), session);
            byAttempt.put(session.getLearningAttemptId(), session.getId());
            return session;
        }

        @Override
        public Optional<ScenarioSession> findById(UUID id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public boolean existsByLearningAttemptId(UUID learningAttemptId) {
            return byAttempt.containsKey(learningAttemptId);
        }
    }
}
