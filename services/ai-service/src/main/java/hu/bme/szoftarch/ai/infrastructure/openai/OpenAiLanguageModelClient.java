package hu.bme.szoftarch.ai.infrastructure.openai;

import hu.bme.szoftarch.ai.domain.ConversationTurn;
import hu.bme.szoftarch.ai.domain.LanguageModelClient;
import hu.bme.szoftarch.ai.domain.LanguageModelException;
import hu.bme.szoftarch.ai.domain.MessageRole;
import hu.bme.szoftarch.ai.domain.ScenarioConfig;
import hu.bme.szoftarch.ai.domain.ScenarioEvaluation;
import hu.bme.szoftarch.ai.domain.TranslationEvaluation;
import hu.bme.szoftarch.ai.domain.TranslationRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * OpenAI-kompatibilis nyelvi modell adapter. A modellt JSON-kimenetre kéri,
 * a választ szigorú record-okba parse-olja és Bean Validationnel ellenőrzi
 * (ez a séma). Sémán kívüli vagy hibás kimenet esetén egyszer újrakér. A
 * hívás timeoutját és minden hibáját {@link LanguageModelException}-re fordítja.
 */
public class OpenAiLanguageModelClient implements LanguageModelClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final String model;

    public OpenAiLanguageModelClient(RestClient restClient, ObjectMapper objectMapper,
                                     Validator validator, String model) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.model = model;
    }

    @Override
    public TranslationEvaluation evaluateTranslation(TranslationRequest request) {
        String system = """
                Egy nyelvtanuló fordítását értékeled. Kizárólag JSON-t adj vissza,
                pontosan ezekkel a mezőkkel: score (0.0-1.0 szám), feedback (rövid szöveg).
                A score a jelentésbeli helyesség és a referenciához való hasonlóság alapján készül.""";
        String user = """
                Forrás: %s
                Célnyelv: %s
                Tanuló fordítása: %s
                Elfogadható referenciák: %s
                Szempontok: %s""".formatted(
                request.sourceText(), request.targetLanguage(), request.learnerTranslation(),
                String.join(" | ", request.referenceTranslations()),
                String.join(" | ", request.evaluationCriteria()));

        TranslationOutput out = callForJson(system, user, TranslationOutput.class);
        return TranslationEvaluation.of(out.score(), out.feedback());
    }

    @Override
    public String continueScenario(ScenarioConfig config, List<ConversationTurn> history) {
        List<ChatWire.ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatWire.ChatMessage("system", scenarioSystemPrompt(config)));
        for (ConversationTurn turn : history) {
            messages.add(new ChatWire.ChatMessage(toApiRole(turn.role()), turn.content()));
        }
        // Szabad szöveges válasz (nem JSON) a beszélgetéshez.
        String content = callRaw(new ChatWire.ChatRequest(model, messages, null, 0.7));
        if (content == null || content.isBlank()) {
            throw new LanguageModelException("A modell üres választ adott a beszélgetésre.");
        }
        return content.strip();
    }

    @Override
    public ScenarioEvaluation evaluateScenario(ScenarioConfig config, List<ConversationTurn> history) {
        String transcript = history.stream()
                .map(t -> t.role() + ": " + t.content())
                .collect(Collectors.joining("\n"));
        String system = """
                Egy nyelvtanuló szituációs beszélgetését értékeled. Kizárólag JSON-t adj vissza,
                pontosan ezekkel a mezőkkel: comprehensibility, grammar, taskCompletion
                (mind 0.0-1.0 szám), feedback (rövid szöveg).""";
        String user = "A szituáció célja: %s\n\nBeszélgetés:\n%s".formatted(config.goal(), transcript);

        ScenarioEvaluationOutput out = callForJson(system, user, ScenarioEvaluationOutput.class);
        return new ScenarioEvaluation(
                out.comprehensibility(), out.grammar(), out.taskCompletion(), out.feedback());
    }

    private String scenarioSystemPrompt(ScenarioConfig config) {
        return """
                Egy nyelvi szituációs gyakorlat szereplője vagy.
                Szereped: %s
                Helyzet: %s
                A tanuló célja: %s
                Nyelv: %s
                Maradj szerepben, válaszolj röviden a célnyelven.""".formatted(
                config.role(), config.situation(), config.goal(), config.targetLanguage());
    }

    private String toApiRole(MessageRole role) {
        return switch (role) {
            case SYSTEM -> "system";
            case USER -> "user";
            case ASSISTANT -> "assistant";
        };
    }

    /**
     * JSON-kimenetet kérő hívás validációval és egyszeri újrapróbálkozással.
     * Ha a parse vagy a séma-validáció másodszorra is bukik, hibát dob.
     */
    private <T> T callForJson(String system, String user, Class<T> type) {
        Supplier<T> attempt = () -> {
            var request = new ChatWire.ChatRequest(
                    model,
                    List.of(new ChatWire.ChatMessage("system", system),
                            new ChatWire.ChatMessage("user", user)),
                    ChatWire.ResponseFormat.jsonObject(),
                    0.2);
            String content = callRaw(request);
            return parseAndValidate(content, type);
        };

        try {
            return attempt.get();
        } catch (LanguageModelException firstFailure) {
            // Egyszeri retry sémahiba/parse-hiba esetén.
            try {
                return attempt.get();
            } catch (LanguageModelException secondFailure) {
                throw new LanguageModelException(
                        "A modell kétszer is érvénytelen kimenetet adott: " + secondFailure.getMessage(),
                        secondFailure);
            }
        }
    }

    private <T> T parseAndValidate(String content, Class<T> type) {
        if (content == null || content.isBlank()) {
            throw new LanguageModelException("A modell üres kimenetet adott.");
        }
        T parsed;
        try {
            parsed = objectMapper.readValue(content, type);
        } catch (RuntimeException ex) {
            throw new LanguageModelException("A modell kimenete nem a várt JSON: " + ex.getMessage(), ex);
        }
        Set<ConstraintViolation<T>> violations = validator.validate(parsed);
        if (!violations.isEmpty()) {
            String details = violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .collect(Collectors.joining(", "));
            throw new LanguageModelException("A modell kimenete nem felel meg a sémának: " + details);
        }
        return parsed;
    }

    /** A nyers HTTP-hívás; a timeoutot a RestClient hordozza. */
    private String callRaw(ChatWire.ChatRequest request) {
        try {
            ChatWire.ChatResponse response = restClient.post()
                    .uri("/chat/completions")
                    .body(request)
                    .retrieve()
                    .body(ChatWire.ChatResponse.class);
            if (response == null) {
                throw new LanguageModelException("A modell nem adott választ.");
            }
            return response.firstContent();
        } catch (LanguageModelException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            // Timeout, hálózati hiba, nem-2xx státusz -> egységes domain hiba.
            throw new LanguageModelException("A nyelvi modell hívása sikertelen: " + ex.getMessage(), ex);
        }
    }
}
