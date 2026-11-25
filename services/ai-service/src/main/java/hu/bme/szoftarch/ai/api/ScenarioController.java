package hu.bme.szoftarch.ai.api;

import hu.bme.szoftarch.ai.api.dto.ScenarioMessageRequestDto;
import hu.bme.szoftarch.ai.api.dto.ScenarioSessionResponseDto;
import hu.bme.szoftarch.ai.api.dto.StartScenarioRequestDto;
import hu.bme.szoftarch.ai.application.ScenarioSessionService;
import hu.bme.szoftarch.ai.domain.ScenarioConfig;
import hu.bme.szoftarch.ai.domain.ScenarioEvaluation;
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

import java.util.UUID;

/**
 * Belső szituáció-session végpontok. Csak a belső hálózaton érhetők el.
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
    public ScenarioSessionResponseDto start(@Valid @RequestBody StartScenarioRequestDto body) {
        var config = new ScenarioConfig(
                body.role(), body.situation(), body.goal(), body.targetLanguage(), body.openingMessage());
        ScenarioSession session = service.start(
                body.learningAttemptId(), body.userId(), body.exerciseId(), config);
        return toDto(session);
    }

    @PostMapping(value = "/{id}/messages",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ScenarioSessionResponseDto sendMessage(
            @PathVariable UUID id, @Valid @RequestBody ScenarioMessageRequestDto body) {
        return toDto(service.addUserMessage(id, body.message()));
    }

    @PostMapping(value = "/{id}/complete", produces = MediaType.APPLICATION_JSON_VALUE)
    public ScenarioSessionResponseDto complete(@PathVariable UUID id) {
        return toDto(service.complete(id));
    }

    private ScenarioSessionResponseDto toDto(ScenarioSession session) {
        var messages = session.getMessages().stream()
                .map(m -> new ScenarioSessionResponseDto.MessageDto(
                        m.sequenceNo(), m.role().name(), m.content()))
                .toList();

        ScenarioSessionResponseDto.EvaluationDto evaluation = null;
        ScenarioEvaluation e = session.getFinalEvaluation();
        if (e != null) {
            evaluation = new ScenarioSessionResponseDto.EvaluationDto(
                    e.comprehensibility(), e.grammar(), e.taskCompletion(),
                    round2(e.overallScore()), e.passed(), e.feedback());
        }

        return new ScenarioSessionResponseDto(
                session.getId(), session.getStatus().name(), session.getUserTurnCount(), messages, evaluation);
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
