package hu.bme.szoftarch.ai.api;

import tools.jackson.databind.ObjectMapper;
import hu.bme.szoftarch.ai.application.TranslationEvaluationService;
import hu.bme.szoftarch.ai.domain.LanguageModelClient;
import hu.bme.szoftarch.ai.infrastructure.FakeLanguageModelClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TranslationController.class,
        excludeAutoConfiguration = {
                org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration.class
        })
@Import(TranslationControllerTest.TestConfig.class)
class TranslationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void acceptedTranslationReturnsHighScore() throws Exception {
        var body = Map.of(
                "sourceText", "A kutya fut.",
                "targetLanguage", "en",
                "learnerTranslation", "The dog runs.",
                "referenceTranslations", List.of("The dog runs."));

        mockMvc.perform(post("/internal/v1/translations:evaluate")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(1.0))
                .andExpect(jsonPath("$.accepted").value(true));
    }

    @Test
    void blankTranslationFailsValidation() throws Exception {
        var body = Map.of(
                "sourceText", "A kutya fut.",
                "targetLanguage", "en",
                "learnerTranslation", "   ",
                "referenceTranslations", List.of("The dog runs."));

        mockMvc.perform(post("/internal/v1/translations:evaluate")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    static class TestConfig {
        @Bean
        LanguageModelClient languageModelClient() {
            return new FakeLanguageModelClient();
        }

        @Bean
        TranslationEvaluationService translationEvaluationService(LanguageModelClient client) {
            return new TranslationEvaluationService(client);
        }
    }
}
