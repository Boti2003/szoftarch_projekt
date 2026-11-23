package hu.bme.szoftarch.ai.api;

import hu.bme.szoftarch.ai.api.dto.TranslationEvaluationRequestDto;
import hu.bme.szoftarch.ai.api.dto.TranslationEvaluationResponseDto;
import hu.bme.szoftarch.ai.application.TranslationEvaluationService;
import hu.bme.szoftarch.ai.domain.TranslationEvaluation;
import hu.bme.szoftarch.ai.domain.TranslationRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Belső fordítás-értékelés végpont. Csak a belső hálózaton érhető el,
 * a Gateway nem route-olja ide a böngészőt.
 */
@RestController
@RequestMapping("/internal/v1")
public class TranslationController {

    private final TranslationEvaluationService service;

    public TranslationController(TranslationEvaluationService service) {
        this.service = service;
    }

    // A ':evaluate' egy művelet-stílusú al-útvonal; a kódolt kettőspont (%3A)
    // helyett a sima karaktert is elfogadjuk.
    @PostMapping(value = {"/translations:evaluate", "/translations%3Aevaluate"},
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public TranslationEvaluationResponseDto evaluate(
            @Valid @RequestBody TranslationEvaluationRequestDto body) {

        TranslationRequest request = new TranslationRequest(
                body.sourceText(),
                body.targetLanguage(),
                body.learnerTranslation(),
                body.referenceTranslations(),
                body.evaluationCriteria());

        TranslationEvaluation result = service.evaluate(request);

        return new TranslationEvaluationResponseDto(
                result.score(), result.accepted(), result.feedback());
    }
}
