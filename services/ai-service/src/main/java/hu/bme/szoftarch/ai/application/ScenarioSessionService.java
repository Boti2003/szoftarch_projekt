package hu.bme.szoftarch.ai.application;

import hu.bme.szoftarch.ai.domain.LanguageModelClient;
import hu.bme.szoftarch.ai.domain.ScenarioConfig;
import hu.bme.szoftarch.ai.domain.ScenarioEvaluation;
import hu.bme.szoftarch.ai.domain.ScenarioSession;
import hu.bme.szoftarch.ai.domain.ScenarioSessionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Szituáció-session use case-ek. A domain aggregate hozza az
 * életciklus-szabályokat, a nyelvi modell adapter a szöveget, a repository
 * a perzisztenciát. A 8. forduló után a session automatikusan lezárul.
 */
@Service
public class ScenarioSessionService {

    private final ScenarioSessionRepository repository;
    private final LanguageModelClient languageModel;
    private final String provider;

    public ScenarioSessionService(ScenarioSessionRepository repository,
                                  LanguageModelClient languageModel,
                                  @Value("${ai.provider:fake}") String provider) {
        this.repository = repository;
        this.languageModel = languageModel;
        this.provider = provider;
    }

    /** Új session indítása a megbízható configgal; a nyitó üzenet a configból jön. */
    @Transactional
    public ScenarioSession start(ScenarioConfig config) {
        var session = ScenarioSession.start(UUID.randomUUID(), provider, config);
        return repository.save(session);
    }

    /**
     * Egy felhasználói forduló: rögzíti a user üzenetet, és visszaadja az AI
     * válaszát. Az AI service nem korlátoz és nem zár le; a fordulószámlálást
     * és a lezárás szabályát a hívó (Learning) kezeli.
     */
    @Transactional
    public ScenarioSession addUserMessage(UUID sessionId, String content) {
        ScenarioSession session = load(sessionId);
        session.recordUserMessage(content);
        String reply = languageModel.continueScenario(session.getConfig(), session.conversationHistory());
        session.recordAssistantMessage(reply);
        return repository.save(session);
    }

    /** Záró értékelés a teljes beszélgetésből. A lezárás idejét a hívó dönti el. */
    @Transactional
    public ScenarioSession complete(UUID sessionId) {
        ScenarioSession session = load(sessionId);

        ScenarioEvaluation evaluation =
                languageModel.evaluateScenario(session.getConfig(), session.conversationHistory());
        session.complete(evaluation);
        return repository.save(session);
    }

    @Transactional(readOnly = true)
    public ScenarioSession get(UUID sessionId) {
        return load(sessionId);
    }

    private ScenarioSession load(UUID sessionId) {
        return repository.findById(sessionId)
                .orElseThrow(() -> new NoSuchElementException("Nincs ilyen session: " + sessionId));
    }
}
