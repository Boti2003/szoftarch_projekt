package hu.bme.szoftarch.ai.application;

import hu.bme.szoftarch.ai.domain.LanguageModelClient;
import hu.bme.szoftarch.ai.domain.TranslationEvaluation;
import hu.bme.szoftarch.ai.domain.TranslationRequest;
import org.springframework.stereotype.Service;

/**
 * Fordítás-értékelés use case. A kérést a konfigurált nyelvi modell
 * adapterhez továbbítja, és annak strukturált eredményét adja vissza.
 */
@Service
public class TranslationEvaluationService {

    private final LanguageModelClient languageModelClient;

    public TranslationEvaluationService(LanguageModelClient languageModelClient) {
        this.languageModelClient = languageModelClient;
    }

    public TranslationEvaluation evaluate(TranslationRequest request) {
        return languageModelClient.evaluateTranslation(request);
    }
}
