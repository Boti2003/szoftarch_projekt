package hu.bme.szoftarch.ai.api;

import hu.bme.szoftarch.ai.application.ScenarioSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

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

    @Test
    void messageToCompletedSessionReturns409() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.addUserMessage(org.mockito.ArgumentMatchers.eq(id), org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new IllegalStateException("A session nem fogad több üzenetet."));

        mockMvc.perform(post("/internal/v1/scenario-sessions/{id}/messages", id)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("message", "late"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("invalid-state"));
    }

    @Test
    void messageToUnknownSessionReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.addUserMessage(org.mockito.ArgumentMatchers.eq(id), org.mockito.ArgumentMatchers.anyString()))
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
