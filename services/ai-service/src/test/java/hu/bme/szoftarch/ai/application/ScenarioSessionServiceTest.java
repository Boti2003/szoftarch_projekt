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
        var session = service.start(config());

        assertThat(session.getStatus()).isEqualTo(SessionStatus.OPEN);
        assertThat(session.getMessages()).hasSize(1);
    }

    @Test
    void eachUserMessageGetsAssistantReply() {
        var started = service.start(config());

        var updated = service.addUserMessage(started.getId(), "I want a table please");

        assertThat(updated.getUserTurnCount()).isEqualTo(1);
        // opening + user + assistant reply
        assertThat(updated.getMessages()).hasSize(3);
    }

    @Test
    void messageNeverAutoCompletesOrLimits() {
        var started = service.start(config());
        UUID id = started.getId();

        ScenarioSession last = null;
        for (int i = 0; i < 12; i++) {
            last = service.addUserMessage(id, "message number " + i + " with some words");
        }

        // Az AI service nem korlátoz és nem zár le: a session OPEN marad,
        // bármennyi üzenet mehet, értékelés nélkül.
        assertThat(last).isNotNull();
        assertThat(last.getStatus()).isEqualTo(SessionStatus.OPEN);
        assertThat(last.getUserTurnCount()).isEqualTo(12);
        assertThat(last.getFinalEvaluation()).isNull();
    }

    @Test
    void completeProducesEvaluationAtAnyTurnCount() {
        var started = service.start(config());
        UUID id = started.getId();
        service.addUserMessage(id, "only one turn here");

        var completed = service.complete(id);

        assertThat(completed.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(completed.getFinalEvaluation()).isNotNull();
    }

    @Test
    void manualCompleteProducesEvaluation() {
        var started = service.start(config());
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

        @Override
        public ScenarioSession save(ScenarioSession session) {
            store.put(session.getId(), session);
            return session;
        }

        @Override
        public Optional<ScenarioSession> findById(UUID id) {
            return Optional.ofNullable(store.get(id));
        }
    }
}
