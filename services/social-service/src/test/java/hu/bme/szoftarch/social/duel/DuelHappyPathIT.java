package hu.bme.szoftarch.social.duel;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import hu.bme.szoftarch.social.api.duel.dto.CreateDuelRequest;
import hu.bme.szoftarch.social.api.duel.dto.SubmitAnswerRequest;
import hu.bme.szoftarch.social.domain.duel.DuelStatus;
import hu.bme.szoftarch.social.domain.duel.ParticipantStatus;
import hu.bme.szoftarch.social.domain.friend.FriendPair;
import hu.bme.szoftarch.social.infrastructure.persistence.DuelParticipantEntity;
import hu.bme.szoftarch.social.infrastructure.persistence.DuelParticipantRepository;
import hu.bme.szoftarch.social.infrastructure.persistence.DuelRepository;
import hu.bme.szoftarch.social.infrastructure.persistence.FriendshipEntity;
import hu.bme.szoftarch.social.infrastructure.persistence.FriendshipRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Full-stack Testcontainers integration test for the duel happy path (task 12.2).
 *
 * <p>Boots the entire Social Service against a real PostgreSQL container (real
 * Flyway V1 + V2, {@code ddl-auto=validate}) with the Identity and Learning
 * outbound adapters pointed at an in-process WireMock server via
 * {@link DynamicPropertySource}. It drives the complete duel lifecycle through
 * the public HTTP API with {@link MockMvc} and the {@code jwt()} post-processor,
 * so every layer — controller, security, use cases, state machine, scoring,
 * JPA persistence, and both adapters — participates.
 *
 * <h2>What is asserted</h2>
 * <ol>
 *   <li><b>Create (Req 3.1):</b> as U1, {@code POST /duels} returns 201 with a
 *       PENDING duel whose {@code learningSetId} is the one Learning minted;
 *       Learning {@code createDuelSet} was called.</li>
 *   <li><b>Accept:</b> as U2, {@code POST /duels/{id}/accept} moves it to
 *       ACCEPTED.</li>
 *   <li><b>Exercises (Req 3.11 context):</b> both participants fetch the same
 *       five exercises in the same order; the first fetch starts the duel
 *       (IN_PROGRESS).</li>
 *   <li><b>Answers + winner (Req 3.4 scoring sum, 3.5 winner):</b> U1 answers
 *       all five correctly (50 pts), U2 answers two correctly (20 pts). When
 *       both complete, the duel is COMPLETED and {@code winnerId == U1}.</li>
 *   <li><b>No profile points (Req 3.7 / Property 12):</b> the whole run issues
 *       no Identity write / profile-point mutation — WireMock records zero
 *       requests to any points/profile mutation path.</li>
 *   <li><b>Result privacy (Req 3.6 context):</b> before U1 completes, the
 *       opponent detail is hidden on {@code GET /duels/{id}}; after both
 *       complete, the winner is visible.</li>
 * </ol>
 *
 * <h2>Scoring determinism</h2>
 * Learning scoring is stubbed to depend only on the submitted answer string:
 * {@code "correct"} yields {@code correct:true, awardedPoints:10}; anything else
 * yields {@code correct:false, awardedPoints:0}. U1 submits {@code "correct"}
 * for all five exercises; U2 submits {@code "correct"} for two and {@code "wrong"}
 * for three. This makes U1 the deterministic winner (50 &gt; 20) independent of
 * timing.
 *
 * <h2>Security</h2>
 * A {@link TestJwtDecoderConfig#jwtDecoder() test JwtDecoder} is provided so the
 * real {@code SecurityConfig} resource server loads without a live JWKS; the
 * {@code jwt()} post-processor authenticates each request and the actor id is
 * taken from the token {@code sub}.
 *
 * <p>Requires a running Docker daemon (Testcontainers). CI runs this; locally it
 * needs the engine API pinned (see the module notes / {@code -Dapi.version}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(DuelHappyPathIT.TestJwtDecoderConfig.class)
class DuelHappyPathIT {

    private static final String MAX_POINTS = "10";
    private static final String CORRECT = "correct";
    private static final String WRONG = "wrong";

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    /** Shared stub for both Identity and Learning internal APIs. */
    static WireMockServer wireMock;

    /** The five exercise ids Learning returns, in a fixed presentation order. */
    static List<UUID> exerciseIds;

    /** The Learning duel-set id minted by the create call. */
    static UUID learningSetId;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMock != null) {
            wireMock.stop();
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        // Real PostgreSQL via the container, schema built by the real migrations.
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");

        // Identity adapter calls absolute /internal/v1/... paths, so its base
        // URL is the server root. Learning adapter uses relative paths under a
        // /internal/v1 base, matching application.yml's ${LEARNING_URL}/internal/v1.
        registry.add("social.identity.base-url", () -> wireMock.baseUrl());
        registry.add("social.learning.base-url", () -> wireMock.baseUrl() + "/internal/v1");

        // Keep the resource-server JWKS property present (a test JwtDecoder bean
        // replaces the real decoder so no JWKS is ever fetched).
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> wireMock.baseUrl() + "/.well-known/jwks.json");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FriendshipRepository friendshipRepository;

    @Autowired
    private DuelRepository duelRepository;

    @Autowired
    private DuelParticipantRepository duelParticipantRepository;

    private UUID u1; // challenger
    private UUID u2; // opponent
    private UUID languageId;

    @BeforeEach
    void resetState() {
        wireMock.resetAll();
        // A clean slice of state per test: remove any rows a prior test left.
        duelParticipantRepository.deleteAll();
        duelRepository.deleteAll();
        friendshipRepository.deleteAll();

        u1 = UUID.randomUUID();
        u2 = UUID.randomUUID();
        languageId = UUID.randomUUID();
        learningSetId = UUID.randomUUID();
        exerciseIds = List.of(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID());

        seedFriendship(u1, u2);
        stubIdentityExistsTrue();
        stubLearning();
    }

    /**
     * Drives create &rarr; accept &rarr; exercises &rarr; answers &rarr;
     * completion and asserts winner determination, shared-exercise ordering,
     * result privacy, and that no profile-point call is made.
     */
    @Test
    void duelHappyPath_u1WinsAndNoProfilePointCallIsMade() throws Exception {
        // --- 1. U1 creates the duel (Req 3.1) ------------------------------
        MvcResult created = mockMvc.perform(post("/api/v1/duels")
                        .with(jwt().jwt(j -> j.subject(u1.toString())))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new CreateDuelRequest(u2, languageId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.challengerId").value(u1.toString()))
                .andExpect(jsonPath("$.opponentId").value(u2.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.learningSetId").value(learningSetId.toString()))
                .andReturn();

        UUID duelId = UUID.fromString(readJson(created).get("id").asString());

        // Learning was asked to build the fixed five-exercise set.
        wireMock.verify(postRequestedFor(urlEqualTo("/internal/v1/duel-sets"))
                .withRequestBody(matching(".*\"languageId\":\"" + languageId + "\".*")));

        // Persisted: PENDING with the learning set id, both participants NOT_STARTED.
        assertThat(duelRepository.findById(duelId)).hasValueSatisfying(d -> {
            assertThat(d.getStatus()).isEqualTo(DuelStatus.PENDING);
            assertThat(d.getLearningSetId()).isEqualTo(learningSetId);
            assertThat(d.getWinnerId()).isNull();
        });

        // --- 2. U2 accepts (PENDING -> ACCEPTED) ---------------------------
        mockMvc.perform(post("/api/v1/duels/{id}/accept", duelId)
                        .with(jwt().jwt(j -> j.subject(u2.toString())))
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk());
        assertThat(duelRepository.findById(duelId).orElseThrow().getStatus())
                .isEqualTo(DuelStatus.ACCEPTED);

        // --- 3. Both fetch the shared exercises (same five, same order) ----
        List<UUID> u1Exercises = fetchExerciseIds(duelId, u1);
        assertThat(u1Exercises).containsExactlyElementsOf(exerciseIds);

        // First fetch starts the duel and the participant.
        assertThat(duelRepository.findById(duelId).orElseThrow().getStatus())
                .isEqualTo(DuelStatus.IN_PROGRESS);
        assertThat(participant(duelId, u1).getStatus()).isEqualTo(ParticipantStatus.IN_PROGRESS);

        List<UUID> u2Exercises = fetchExerciseIds(duelId, u2);
        assertThat(u2Exercises).containsExactlyElementsOf(exerciseIds);
        assertThat(u2Exercises).containsExactlyElementsOf(u1Exercises);

        // --- 6a. Result privacy: before U1 completes, opponent is hidden ---
        mockMvc.perform(get("/api/v1/duels/{id}", duelId)
                        .with(jwt().jwt(j -> j.subject(u1.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.self.userId").value(u1.toString()))
                .andExpect(jsonPath("$.opponent").doesNotExist())
                .andExpect(jsonPath("$.winnerId").doesNotExist());

        // --- 4. U1 answers all five correctly (50 pts), completing -------
        for (int i = 0; i < 5; i++) {
            submitAnswer(duelId, u1, i, CORRECT);
        }
        // U1 has completed its side; the duel is still IN_PROGRESS (U2 pending).
        DuelParticipantEntity u1Participant = participant(duelId, u1);
        assertThat(u1Participant.getStatus()).isEqualTo(ParticipantStatus.COMPLETED);
        assertThat(u1Participant.getScore()).isEqualTo(50);
        assertThat(u1Participant.getAnsweredCount()).isEqualTo(5);
        assertThat(duelRepository.findById(duelId).orElseThrow().getStatus())
                .isEqualTo(DuelStatus.IN_PROGRESS);

        // --- 4b. U2 answers two correct, three wrong (20 pts), completing --
        String[] u2Answers = {CORRECT, CORRECT, WRONG, WRONG, WRONG};
        for (int i = 0; i < 5; i++) {
            MvcResult answer = submitAnswer(duelId, u2, i, u2Answers[i]);
            if (i == 4) {
                // The fifth answer completes U2 and, with U1 already done, the duel.
                JsonNode body = readJson(answer);
                assertThat(body.get("participantStatus").asString()).isEqualTo("COMPLETED");
                assertThat(body.get("duelStatus").asString()).isEqualTo("COMPLETED");
                assertThat(body.get("winnerId").asString()).isEqualTo(u1.toString());
            }
        }

        DuelParticipantEntity u2Participant = participant(duelId, u2);
        assertThat(u2Participant.getStatus()).isEqualTo(ParticipantStatus.COMPLETED);
        assertThat(u2Participant.getScore()).isEqualTo(20);

        // --- 5. Winner determination (Req 3.4 sum, Req 3.5 higher total) ---
        assertThat(duelRepository.findById(duelId)).hasValueSatisfying(d -> {
            assertThat(d.getStatus()).isEqualTo(DuelStatus.COMPLETED);
            // U1 (50) beats U2 (20); winner is the higher total, timing ignored.
            assertThat(d.getWinnerId()).isEqualTo(u1);
        });

        // --- 6b. Result privacy: after both complete, winner is visible ----
        mockMvc.perform(get("/api/v1/duels/{id}", duelId)
                        .with(jwt().jwt(j -> j.subject(u2.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.winnerId").value(u1.toString()))
                // U2 has completed, so the opponent (U1) detail is now exposed.
                .andExpect(jsonPath("$.opponent.userId").value(u1.toString()))
                .andExpect(jsonPath("$.opponent.score").value(50));

        // --- 7. No profile-point / Identity write mutation (Property 12) ---
        assertNoIdentityProfilePointMutation();
    }

    /**
     * A draw variant kept cheap: when both participants score the same total the
     * duel completes with {@code winnerId == null} (Req 3.5 draw branch). Only
     * the scoring stub changes — U2 also answers all five correctly.
     */
    @Test
    void duelHappyPath_equalTotalsProduceADraw() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/duels")
                        .with(jwt().jwt(j -> j.subject(u1.toString())))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new CreateDuelRequest(u2, languageId))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID duelId = UUID.fromString(readJson(created).get("id").asString());

        mockMvc.perform(post("/api/v1/duels/{id}/accept", duelId)
                        .with(jwt().jwt(j -> j.subject(u2.toString())))
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk());

        fetchExerciseIds(duelId, u1);
        fetchExerciseIds(duelId, u2);

        for (int i = 0; i < 5; i++) {
            submitAnswer(duelId, u1, i, CORRECT);
        }
        for (int i = 0; i < 5; i++) {
            submitAnswer(duelId, u2, i, CORRECT);
        }

        assertThat(duelRepository.findById(duelId)).hasValueSatisfying(d -> {
            assertThat(d.getStatus()).isEqualTo(DuelStatus.COMPLETED);
            // Equal totals (50 == 50) => draw => null winner.
            assertThat(d.getWinnerId()).isNull();
        });

        assertNoIdentityProfilePointMutation();
    }

    // --- helpers -----------------------------------------------------------

    private List<UUID> fetchExerciseIds(UUID duelId, UUID actor) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/duels/{id}/exercises", duelId)
                        .with(jwt().jwt(j -> j.subject(actor.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andReturn();
        JsonNode array = readJson(result);
        return array.valueStream()
                .map(node -> UUID.fromString(node.get("exerciseId").asString()))
                .toList();
    }

    private MvcResult submitAnswer(UUID duelId, UUID actor, int position, String answer)
            throws Exception {
        return mockMvc.perform(post("/api/v1/duels/{id}/answers", duelId)
                        .with(jwt().jwt(j -> j.subject(actor.toString())))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new SubmitAnswerRequest(position,
                                        java.util.Map.of("selectedOptionIds", java.util.List.of(answer))))))
                .andExpect(status().isOk())
                .andReturn();
    }

    private DuelParticipantEntity participant(UUID duelId, UUID userId) {
        return duelParticipantRepository.findByDuelIdAndUserId(duelId, userId).orElseThrow();
    }

    private JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private void seedFriendship(UUID a, UUID b) {
        FriendPair pair = FriendPair.of(a, b);
        friendshipRepository.saveAndFlush(
                new FriendshipEntity(UUID.randomUUID(), pair.low(), pair.high(), Instant.now()));
    }

    /** Both users exist according to Identity (covers the create-time check). */
    private void stubIdentityExistsTrue() {
        // Social derives existence from the batch public-stats call.
        wireMock.stubFor(WireMock.post(urlEqualTo("/internal/v1/users/public-stats:batch"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[{\"id\":\"" + u1 + "\",\"username\":\"u1\",\"displayName\":\"U1\",\"level\":1,\"totalPoints\":0},"
                                + "{\"id\":\"" + u2 + "\",\"username\":\"u2\",\"displayName\":\"U2\",\"level\":1,\"totalPoints\":0}]")));
    }

    /**
     * Stubs the three Learning endpoints:
     * <ul>
     *   <li>create &rarr; the fixed {@link #learningSetId} with exerciseCount 5,</li>
     *   <li>fetch &rarr; the five {@link #exerciseIds} in a stable order,</li>
     *   <li>score &rarr; correct iff the submitted {@code answer} is {@code "correct"}.</li>
     * </ul>
     */
    private void stubLearning() {
        // Learning returns {setId, exercises[]} with typed presentations in
        // position order; both participants get the same five exercises.
        StringBuilder exercises = new StringBuilder("[");
        for (int i = 0; i < exerciseIds.size(); i++) {
            if (i > 0) {
                exercises.append(',');
            }
            exercises.append("{\"exerciseId\":\"").append(exerciseIds.get(i))
                    .append("\",\"type\":\"MULTIPLE_CHOICE\",\"prompt\":\"q").append(i)
                    .append("\",\"maxPoints\":").append(MAX_POINTS)
                    .append(",\"content\":{\"multiSelect\":false,\"options\":"
                            + "[{\"id\":\"correct\",\"text\":\"C\"},{\"id\":\"wrong\",\"text\":\"W\"}]}}");
        }
        exercises.append(']');
        String setBody = "{\"setId\":\"" + learningSetId + "\",\"exercises\":" + exercises + "}";

        wireMock.stubFor(WireMock.post(urlEqualTo("/internal/v1/duel-sets"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(setBody)));
        wireMock.stubFor(WireMock.get(urlEqualTo("/internal/v1/duel-sets/" + learningSetId))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(setBody)));

        // Answer scoring is deterministic by the submitted option id: "correct"
        // -> full points, anything else -> 0. Returns {position, score, correct}.
        wireMock.stubFor(WireMock.post(urlPathMatching("/internal/v1/duel-sets/[0-9a-fA-F-]+/answers"))
                .withRequestBody(matching(".*\"selectedOptionIds\":\\[\"" + CORRECT + "\"\\].*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"position\":0,\"score\":" + MAX_POINTS + ",\"correct\":true}")));
        wireMock.stubFor(WireMock.post(urlPathMatching("/internal/v1/duel-sets/[0-9a-fA-F-]+/answers"))
                .withRequestBody(matching(".*\"selectedOptionIds\":\\[\"" + WRONG + "\"\\].*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"position\":0,\"score\":0,\"correct\":false}")));
    }

    /**
     * Property 12 (Req 3.7): completing a duel produces no Identity profile-point
     * mutation. Social defines no points/profile write endpoint, so the only
     * Identity requests the run may issue are the existence read and the (unused
     * here) public-stats batch. This asserts zero requests to any plausible
     * points/profile mutation path, and that every POST that did reach the stub
     * server targeted a Learning duel-set endpoint — never Identity.
     */
    private void assertNoIdentityProfilePointMutation() {
        // No request to any points / profile mutation endpoint, by any method.
        wireMock.verify(0, anyRequestedFor(
                urlMatching("/internal/v1/users/.*/(points|profile).*")));
        wireMock.verify(0, anyRequestedFor(urlMatching("/internal/v1/users/.*/points")));
        wireMock.verify(0, anyRequestedFor(urlMatching(".*points.*")));

        // Every POST that reached the server was a Learning duel-set call; no
        // POST was made to any Identity (/users) path.
        assertThat(wireMock.findAll(postRequestedFor(anyUrl())))
                .as("all POSTs target Learning duel-sets; none write to Identity")
                .allSatisfy(req -> assertThat(req.getUrl())
                        .startsWith("/internal/v1/duel-sets"));
        wireMock.verify(0, postRequestedFor(urlPathMatching("/internal/v1/users/.*")));
    }

    /**
     * Supplies a test {@link JwtDecoder} so the production resource-server
     * security chain loads without reaching a live JWKS. Each decode yields a
     * minimal {@link Jwt} whose {@code sub} the {@code jwt()} post-processor has
     * already set on the security context; this bean exists only so the context
     * starts, and is marked {@link Primary} to win over the lazy production
     * decoder.
     */
    @TestConfiguration
    static class TestJwtDecoderConfig {

        @Bean
        @Primary
        JwtDecoder testJwtDecoder() {
            return token -> Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .subject("00000000-0000-0000-0000-000000000000")
                    .claim("scope", "")
                    .build();
        }
    }
}
