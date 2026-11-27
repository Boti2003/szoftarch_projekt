package hu.bme.szoftarch.learning;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import hu.bme.szoftarch.learning.support.TestJwtFactory;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import org.springframework.security.web.FilterChainProxy;

@SpringBootTest
@ActiveProfiles("test")
class SecurityConfigTest {

    private final MockMvc mockMvc;

    @Autowired
    SecurityConfigTest(WebApplicationContext context, FilterChainProxy springSecurityFilterChain) {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    @Test
    void protectedApiEndpointReturns401WithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/test/echo"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedApiEndpointAccessibleWithValidToken() throws Exception {
        String userId = UUID.randomUUID().toString();
        String token = TestJwtFactory.validToken(userId);
        mockMvc.perform(get("/api/v1/test/echo")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId));
    }

    @Test
    void healthEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/api/v1/learning/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void actuatorHealthIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void internalEndpointRejectedWithoutApiKey() throws Exception {
        mockMvc.perform(get("/internal/v1/anything"))
                .andExpect(status().isUnauthorized());
    }
}
