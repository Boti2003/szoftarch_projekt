package hu.bme.szoftarch.ai.infrastructure;

import hu.bme.szoftarch.ai.domain.LanguageModelClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * A futásidejű nyelvi modell provider kiválasztása az {@code AI_PROVIDER}
 * környezeti változó alapján. A domain és application réteg csak a
 * {@link LanguageModelClient} portot látja, a konkrét adaptert nem.
 */
@Configuration
public class LanguageModelConfig {

    @Bean
    public LanguageModelClient languageModelClient(
            @Value("${ai.provider:fake}") String provider) {
        return switch (provider.toLowerCase()) {
            case "fake" -> new FakeLanguageModelClient();
            case "openai-compatible" -> throw new IllegalStateException(
                    "Az openai-compatible provider még nincs implementálva. "
                            + "Állítsd az AI_PROVIDER=fake értéket a fejlesztéshez.");
            default -> throw new IllegalStateException("Ismeretlen AI_PROVIDER: " + provider);
        };
    }
}
