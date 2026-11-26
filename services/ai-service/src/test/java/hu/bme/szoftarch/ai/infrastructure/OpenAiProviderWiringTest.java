package hu.bme.szoftarch.ai.infrastructure;

import hu.bme.szoftarch.ai.domain.LanguageModelClient;
import hu.bme.szoftarch.ai.infrastructure.openai.OpenAiLanguageModelClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ellenőrzi, hogy az openai-compatible provider bean-wiringje felépül, ha van
 * API-kulcs. A context a teszt-profilon (H2) fut; csak a provider-választást
 * és a RestClient-építést validáljuk, valódi hívás nélkül.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "ai.provider=openai-compatible",
        "ai.openai.api-key=test-key",
        "ai.openai.base-url=http://llm.test/v1",
        "ai.openai.model=test-model"
})
class OpenAiProviderWiringTest {

    @Autowired
    private LanguageModelClient languageModelClient;

    @Test
    void resolvesOpenAiAdapter() {
        assertThat(languageModelClient).isInstanceOf(OpenAiLanguageModelClient.class);
    }
}
