package hu.bme.szoftarch.ai.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Szituáció-session aggregate. Spring- és JPA-független; a teljes
 * életciklus-logikát (fordulószámlálás, lezárhatóság, auto-lezárás) ez
 * hordozza, így önállóan, kontextus nélkül tesztelhető.
 */
public class ScenarioSession {

    /** Alsó/felső felhasználói forduló-korlát a lezáráshoz. */
    public static final int MIN_USER_TURNS_TO_COMPLETE = 3;
    public static final int MAX_USER_TURNS = 8;

    private final UUID id;
    private final UUID learningAttemptId;
    private final UUID userId;
    private final UUID exerciseId;
    private final String provider;
    private final ScenarioConfig config;

    private SessionStatus status;
    private int userTurnCount;
    private ScenarioEvaluation finalEvaluation;
    private final List<ScenarioMessage> messages;

    /** Új session a nyitó AI-üzenettel. */
    public static ScenarioSession start(UUID id, UUID learningAttemptId, UUID userId,
                                        UUID exerciseId, String provider, ScenarioConfig config) {
        var session = new ScenarioSession(id, learningAttemptId, userId, exerciseId, provider, config,
                SessionStatus.OPEN, 0, null, new ArrayList<>());
        session.appendMessage(MessageRole.ASSISTANT, config.openingMessage());
        return session;
    }

    /** Teljes rekonstrukció perzisztenciából. */
    public static ScenarioSession rehydrate(UUID id, UUID learningAttemptId, UUID userId,
                                            UUID exerciseId, String provider, ScenarioConfig config,
                                            SessionStatus status, int userTurnCount,
                                            ScenarioEvaluation finalEvaluation,
                                            List<ScenarioMessage> messages) {
        return new ScenarioSession(id, learningAttemptId, userId, exerciseId, provider, config,
                status, userTurnCount, finalEvaluation, new ArrayList<>(messages));
    }

    private ScenarioSession(UUID id, UUID learningAttemptId, UUID userId, UUID exerciseId,
                            String provider, ScenarioConfig config, SessionStatus status,
                            int userTurnCount, ScenarioEvaluation finalEvaluation,
                            List<ScenarioMessage> messages) {
        this.id = id;
        this.learningAttemptId = learningAttemptId;
        this.userId = userId;
        this.exerciseId = exerciseId;
        this.provider = provider;
        this.config = config;
        this.status = status;
        this.userTurnCount = userTurnCount;
        this.finalEvaluation = finalEvaluation;
        this.messages = messages;
    }

    /**
     * Felhasználói forduló rögzítése, majd az AI válaszának hozzáfűzése.
     * Nem állít elő választ; azt a hívó use case kéri az adaptertől.
     */
    public void recordUserMessage(String content) {
        if (status != SessionStatus.OPEN) {
            throw new IllegalStateException("A session lezárt, nem fogad több üzenetet.");
        }
        if (userTurnCount >= MAX_USER_TURNS) {
            throw new IllegalStateException("A session elérte a maximális fordulószámot.");
        }
        appendMessage(MessageRole.USER, content);
        userTurnCount++;
    }

    /** Az AI válaszának rögzítése egy felhasználói forduló után. */
    public void recordAssistantMessage(String content) {
        appendMessage(MessageRole.ASSISTANT, content);
    }

    /** Lezárás a záró értékeléssel. */
    public void complete(ScenarioEvaluation evaluation) {
        if (status != SessionStatus.OPEN) {
            throw new IllegalStateException("A session már lezárt.");
        }
        if (userTurnCount < MIN_USER_TURNS_TO_COMPLETE) {
            throw new IllegalStateException(
                    "A session legalább " + MIN_USER_TURNS_TO_COMPLETE + " forduló után zárható le.");
        }
        this.finalEvaluation = evaluation;
        this.status = SessionStatus.COMPLETED;
    }

    private void appendMessage(MessageRole role, String content) {
        messages.add(new ScenarioMessage(UUID.randomUUID(), messages.size(), role, content));
    }

    /** Fogadhat-e még felhasználói fordulót. */
    public boolean canAcceptUserTurn() {
        return status == SessionStatus.OPEN && userTurnCount < MAX_USER_TURNS;
    }

    /** Lezárható-e (nyitott és elérte a minimumot). */
    public boolean canComplete() {
        return status == SessionStatus.OPEN && userTurnCount >= MIN_USER_TURNS_TO_COMPLETE;
    }

    /** A felső korlát elérésekor kötelező az automatikus lezárás. */
    public boolean mustAutoComplete() {
        return status == SessionStatus.OPEN && userTurnCount >= MAX_USER_TURNS;
    }

    /** A beszélgetés fordulói az adapternek átadható formában. */
    public List<ConversationTurn> conversationHistory() {
        return messages.stream()
                .map(m -> new ConversationTurn(m.role(), m.content()))
                .toList();
    }

    public UUID getId() {
        return id;
    }

    public UUID getLearningAttemptId() {
        return learningAttemptId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getExerciseId() {
        return exerciseId;
    }

    public String getProvider() {
        return provider;
    }

    public ScenarioConfig getConfig() {
        return config;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public int getUserTurnCount() {
        return userTurnCount;
    }

    public ScenarioEvaluation getFinalEvaluation() {
        return finalEvaluation;
    }

    public List<ScenarioMessage> getMessages() {
        return List.copyOf(messages);
    }
}
