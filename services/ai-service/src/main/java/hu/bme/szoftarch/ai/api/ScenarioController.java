package hu.bme.szoftarch.ai.api;

import hu.bme.szoftarch.ai.api.dto.ScenarioEvaluationResponseDto;
import hu.bme.szoftarch.ai.api.dto.ScenarioMessageRequestDto;
import hu.bme.szoftarch.ai.api.dto.ScenarioMessageResponseDto;
import hu.bme.szoftarch.ai.api.dto.StartScenarioRequestDto;
import hu.bme.szoftarch.ai.api.dto.StartScenarioResponseDto;
import hu.bme.szoftarch.ai.application.ScenarioSessionService;
import hu.bme.szoftarch.ai.domain.MessageRole;
import hu.bme.szoftarch.ai.domain.ScenarioConfig;
import hu.bme.szoftarch.ai.domain.ScenarioEvaluation;
import hu.bme.szoftarch.ai.domain.ScenarioMessage;
import hu.bme.szoftarch.ai.domain.ScenarioSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Belső szituáció-session végpontok. Csak a belső hálózaton érhetők el.
 * A válaszok szűk, a hívó Learning service-szel egyeztetett DTO-k.
 */
@RestController
@RequestMapping("/internal/v1/scenario-sessions")
public class ScenarioController {

    private final ScenarioSessionService service;

    public ScenarioController(ScenarioSessionService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public StartScenarioResponseDto start(@Valid @RequestBody StartScenarioRequestDto body) {
        // A goals lista egy szöveges céllá fűzve kerül a domain configba.
        String goal = String.join("; ", body.goals());
        var config = new ScenarioConfig(
                body.role(), body.situation(), goal, body.targetLanguageCode(), body.openingMessage());

        ScenarioSession session = service.start(
                body.learningAttemptId(), body.userId(), body.exerciseId(), config);

        return new StartScenarioResponseDto(session.getId(), openingMessageOf(session));
    }

    @PostMapping(value = "/{id}/messages",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ScenarioMessageResponseDto sendMessage(
            @PathVariable UUID id, @Valid @RequestBody ScenarioMessageRequestDto body) {
        ScenarioSession session = service.addUserMessage(id, body.message());
        return new ScenarioMessageResponseDto(lastAssistantMessageOf(session), session.getUserTurnCount());
    }

    @PostMapping(value = "/{id}/complete", produces = MediaType.APPLICATION_JSON_VALUE)
    public ScenarioEvaluationResponseDto complete(@PathVariable UUID id) {
        ScenarioSession session = service.complete(id);
        ScenarioEvaluation e = session.getFinalEvaluation();
        return new ScenarioEvaluationResponseDto(
                e.comprehensibility(), e.grammar(), e.taskCompletion(), e.feedback());
    }

    private String openingMessageOf(ScenarioSession session) {
        return session.getMessages().stream()
                .filter(m -> m.role() == MessageRole.ASSISTANT)
                .map(ScenarioMessage::content)
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("A session nem tartalmaz nyitó üzenetet."));
    }

    private String lastAssistantMessageOf(ScenarioSession session) {
        return session.getMessages().stream()
                .filter(m -> m.role() == MessageRole.ASSISTANT)
                .reduce((first, second) -> second)
                .map(ScenarioMessage::content)
                .orElseThrow(() -> new NoSuchElementException("A session nem tartalmaz AI üzenetet."));
    }
}
