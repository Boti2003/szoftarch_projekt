package hu.bme.szoftarch.ai.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScenarioSessionTest {

    private ScenarioSession newSession() {
        var config = new ScenarioConfig("waiter", "restaurant", "order food", "en", "Welcome! How can I help?");
        return ScenarioSession.start(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "fake", config);
    }

    @Test
    void startsWithOpeningAssistantMessage() {
        var session = newSession();

        assertThat(session.getStatus()).isEqualTo(SessionStatus.OPEN);
        assertThat(session.getUserTurnCount()).isZero();
        assertThat(session.getMessages()).hasSize(1);
        assertThat(session.getMessages().get(0).role()).isEqualTo(MessageRole.ASSISTANT);
    }

    @Test
    void userMessageIncrementsTurnCount() {
        var session = newSession();

        session.recordUserMessage("I would like a table.");

        assertThat(session.getUserTurnCount()).isEqualTo(1);
        assertThat(session.getMessages()).hasSize(2);
        assertThat(session.getMessages().get(1).role()).isEqualTo(MessageRole.USER);
    }

    @Test
    void cannotCompleteBeforeMinimumTurns() {
        var session = newSession();
        session.recordUserMessage("one");
        session.recordUserMessage("two");

        assertThat(session.canComplete()).isFalse();
        assertThatThrownBy(() -> session.complete(anyEvaluation()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void completesAfterMinimumTurns() {
        var session = newSession();
        session.recordUserMessage("one");
        session.recordUserMessage("two");
        session.recordUserMessage("three");

        assertThat(session.canComplete()).isTrue();
        session.complete(anyEvaluation());

        assertThat(session.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(session.getFinalEvaluation()).isNotNull();
    }

    @Test
    void mustAutoCompleteAtMaxTurns() {
        var session = newSession();
        for (int i = 0; i < ScenarioSession.MAX_USER_TURNS; i++) {
            session.recordUserMessage("turn " + i);
        }

        assertThat(session.mustAutoComplete()).isTrue();
        assertThat(session.canAcceptUserTurn()).isFalse();
    }

    @Test
    void rejectsUserMessageWhenCompleted() {
        var session = newSession();
        session.recordUserMessage("one");
        session.recordUserMessage("two");
        session.recordUserMessage("three");
        session.complete(anyEvaluation());

        assertThatThrownBy(() -> session.recordUserMessage("late"))
                .isInstanceOf(IllegalStateException.class);
    }

    private ScenarioEvaluation anyEvaluation() {
        return new ScenarioEvaluation(0.8, 0.8, 0.8, "ok");
    }
}
