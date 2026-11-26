package hu.bme.szoftarch.ai.infrastructure.openai;

import hu.bme.szoftarch.ai.domain.LanguageModelException;
import hu.bme.szoftarch.ai.domain.TranslationEvaluation;
import hu.bme.szoftarch.ai.domain.TranslationRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;

class OpenAiLanguageModelClientTest {

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private OpenAiLanguageModelClient client;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder().baseUrl("http://llm.test/v1");
        server = MockRestServiceServer.bindTo(builder).build();
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        client = new OpenAiLanguageModelClient(builder.build(), new ObjectMapper(), validator, "test-model");
    }

    private String chatResponse(String contentJson) {
        // A content egy JSON string a válasz 'content' mezőjében (escape-elve).
        String escaped = contentJson.replace("\"", "\\\"");
        return "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"" + escaped + "\"}}]}";
    }

    private TranslationRequest request() {
        return new TranslationRequest("A kutya fut.", "en", "The dog runs.",
                List.of("The dog runs."), List.of());
    }

    @Test
    void validOutputProducesEvaluation() {
        server.expect(requestTo("http://llm.test/v1/chat/completions"))
                .andExpect(method(POST))
                .andRespond(withSuccess(
                        chatResponse("{\"score\":0.9,\"feedback\":\"jó\"}"), MediaType.APPLICATION_JSON));

        TranslationEvaluation result = client.evaluateTranslation(request());

        assertThat(result.score()).isEqualTo(0.9);
        assertThat(result.accepted()).isTrue();
        server.verify();
    }

    @Test
    void retriesOnceOnSchemaViolationThenSucceeds() {
        // Első válasz: score a tartományon kívül -> séma-sértés.
        server.expect(requestTo("http://llm.test/v1/chat/completions"))
                .andRespond(withSuccess(
                        chatResponse("{\"score\":2.0,\"feedback\":\"hibás\"}"), MediaType.APPLICATION_JSON));
        // Második válasz: érvényes.
        server.expect(requestTo("http://llm.test/v1/chat/completions"))
                .andRespond(withSuccess(
                        chatResponse("{\"score\":0.75,\"feedback\":\"ok\"}"), MediaType.APPLICATION_JSON));

        TranslationEvaluation result = client.evaluateTranslation(request());

        assertThat(result.score()).isEqualTo(0.75);
        server.verify();
    }

    @Test
    void failsAfterTwoInvalidOutputs() {
        server.expect(requestTo("http://llm.test/v1/chat/completions"))
                .andRespond(withSuccess(chatResponse("not json at all"), MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://llm.test/v1/chat/completions"))
                .andRespond(withSuccess(chatResponse("{\"feedback\":\"hiányzó score\"}"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.evaluateTranslation(request()))
                .isInstanceOf(LanguageModelException.class);
        server.verify();
    }
}
