package hu.bme.szoftarch.ai.infrastructure.openai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Az OpenAI-kompatibilis provider konfigurációja. Az API-kulcs kizárólag
 * környezeti változóból jön, soha nem kerül kódba.
 *
 * @param baseUrl               a chat completions API gyökér URL-je
 * @param apiKey                a hitelesítő kulcs (env secret)
 * @param model                 a használt modell neve
 * @param requestTimeoutSeconds a külső hívás timeoutja másodpercben
 */
@ConfigurationProperties(prefix = "ai.openai")
public record OpenAiProperties(
        String baseUrl,
        String apiKey,
        String model,
        Integer requestTimeoutSeconds) {

    public OpenAiProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.openai.com/v1";
        }
        if (model == null || model.isBlank()) {
            model = "gpt-4o-mini";
        }
        if (requestTimeoutSeconds == null || requestTimeoutSeconds <= 0) {
            requestTimeoutSeconds = 20;
        }
    }
}
