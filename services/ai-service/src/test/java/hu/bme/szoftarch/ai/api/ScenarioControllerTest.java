package hu.bme.szoftarch.ai.api;

import hu.bme.szoftarch.ai.application.ScenarioSessionService;
import hu.bme.szoftarch.ai.domain.ScenarioConfig;
import hu.bme.szoftarch.ai.domain.ScenarioEvaluation;
import hu.bme.szoftarch.ai.domain.ScenarioSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ScenarioController.class,
        excludeAutoConfiguration = {
                org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration.class
        })
@Import(ScenarioControllerTest.TestConfig.class)
class ScenarioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ScenarioSessionService service;

    private ScenarioSession sampleSession() {
        var config = new ScenarioConfig("waiter", "restaurant", "order food", "en", "Welcome!");
        return ScenarioSession.start(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "fake", config);
    }

    private Map<String, Object> validStartBody() {
        return Map.of(
                "learningAttemptId", UUID.randomUUID().toString(),
                "userId", UUID.randomUUID().toString(),
                "exerciseId", UUID.randomUUID().toString(),
                "role", "waiter",
                "situation", "restaurant",
                "goals", List.of("order food", "pay the bill"),
                "targetLanguageCode", "en",
                "openingMessage", "Welcome!");
    }

    @Test
    void startReturnsIdAndOpeningMessage() throws Exception {
        when(service.start(any(), any(), any(), any())).thenReturn(sampleSession());

        mockMvc.perform(post("/internal/v1/scenario-sessions")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validStartBody())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.openingMessage").value("Welcome!"));
    }

    @Test
    void startWithMissingConfigFieldReturns400() throws Exception {
        var body = new java.util.HashMap<>(validStartBody());
        body.remove("openingMessage");

        mockMvc.perform(post("/internal/v1/scenario-sessions")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void messageReturnsAiMessageAndTurnCount() throws Exception {
        var session = sampleSession();
        session.recordUserMessage("I want a table");
        session.recordAssistantMessage("(waiter) Of course, for how many?");
        when(service.addUserMessage(any(), anyString())).thenReturn(session);

        mockMvc.perform(post("/internal/v1/scenario-sessions/{id}/messages", UUID.randomUUID())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("message", "I want a table"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiMessage").value("(waiter) Of course, for how many?"))
                .andExpect(jsonPath("$.turnCount").value(1));
    }

    @Test
    void completeReturnsThreeSubscoresAndFeedback() throws Exception {
        var session = sampleSession();
        session.recordUserMessage("one");
        session.recordUserMessage("two");
        session.recordUserMessage("three");
        session.complete(new ScenarioEvaluation(0.7, 0.8, 0.9, "nice"));
        when(service.complete(any())).thenReturn(session);

        mockMvc.perform(post("/internal/v1/scenario-sessions/{id}/complete", UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comprehensibility").value(0.7))
                .andExpect(jsonPath("$.grammar").value(0.8))
                .andExpect(jsonPath("$.taskCompletion").value(0.9))
                .andExpect(jsonPath("$.feedback").value("nice"))
                // A belsĹ‘ overallScore/passed NEM szivĂˇrog ki a vĂˇlaszba.
                .andExpect(jsonPath("$.overallScore").doesNotExist())
                .andExpect(jsonPath("$.passed").doesNotExist());
    }

    @Test
    void messageToCompletedSessionReturns409() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.addUserMessage(eq(id), anyString()))
                .thenThrow(new IllegalStateException("A session nem fogad tĂ¶bb ĂĽzenetet."));

        mockMvc.perform(post("/internal/v1/scenario-sessions/{id}/messages", id)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("message", "late"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("invalid-state"));
    }

    @Test
    void messageToUnknownSessionReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.addUserMessage(eq(id), anyString()))
                .thenThrow(new NoSuchElementException("Nincs ilyen session: " + id));

        mockMvc.perform(post("/internal/v1/scenario-sessions/{id}/messages", id)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("message", "hello"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not-found"));
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        ScenarioSessionService scenarioSessionService() {
            return mock(ScenarioSessionService.class);
        }
    }
}

