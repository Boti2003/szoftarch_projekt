package hu.bme.szoftarch.learning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import hu.bme.szoftarch.learning.api.dto.DailyChallengeTodayResponse;
import hu.bme.szoftarch.learning.application.DailyChallengeService;
import hu.bme.szoftarch.learning.application.PointAwardService;
import hu.bme.szoftarch.learning.domain.daily.BusinessClock;
import hu.bme.szoftarch.learning.domain.model.PointAwardStatus;
import hu.bme.szoftarch.learning.domain.model.ProgressStatus;
import hu.bme.szoftarch.learning.domain.model.UserDailyProgress;
import hu.bme.szoftarch.learning.domain.model.UserDailyProgressId;
import hu.bme.szoftarch.learning.domain.port.AwardPointsCommand;
import hu.bme.szoftarch.learning.domain.port.AwardPointsResult;
import hu.bme.szoftarch.learning.domain.port.IdentityPointsPort;
import hu.bme.szoftarch.learning.domain.repository.UserDailyProgressRepository;
import hu.bme.szoftarch.learning.support.ExerciseSamples;
import hu.bme.szoftarch.learning.support.TestLearningData;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * A napi bónusz életciklusának tesztje (TERV 5.4, 7.8) H2-n, mockolt
 * {@link IdentityPointsPort}-tal: a teljesítés PENDING-re élesíti a bónuszt a
 * {@code daily:{userId}:{date}:bonus} kulccsal; a küldés egyszer AWARDED-ra
 * állít; az ismétlés SOHA nem ír dupla pontot (a +20 az U5 adapteren megy).
 */
@SpringBootTest
@ActiveProfiles("test")
class DailyBonusLifecycleTest {

    private final DailyChallengeService dailyChallengeService;
    private final PointAwardService pointAwardService;
    private final UserDailyProgressRepository progressRepository;
    private final BusinessClock businessClock;
    private final TestLearningData data;
    private final ObjectMapper objectMapper;

    @MockitoBean
    private IdentityPointsPort identityPointsPort;

    @Autowired
    DailyBonusLifecycleTest(DailyChallengeService dailyChallengeService,
            PointAwardService pointAwardService, UserDailyProgressRepository progressRepository,
            BusinessClock businessClock, TestLearningData data, ObjectMapper objectMapper) {
        this.dailyChallengeService = dailyChallengeService;
        this.pointAwardService = pointAwardService;
        this.progressRepository = progressRepository;
        this.businessClock = businessClock;
        this.data = data;
        this.objectMapper = objectMapper;
    }

    @BeforeEach
    void cleanUp() {
        data.deleteAll();
    }

    private JsonNode json(String raw) {
        return objectMapper.readTree(raw);
    }

    // Helyes válaszok a seedelt mintákból.
    private static final String TRUE_FALSE_CORRECT = "{\"answer\": false}";
    private static final String MULTIPLE_CHOICE_CORRECT = "{\"selectedOptionIds\": [\"b\"]}";
    private static final String MATCHING_CORRECT =
            "{\"pairs\": [{\"leftId\": \"l1\", \"rightId\": \"r1\"}, {\"leftId\": \"l2\", \"rightId\": \"r2\"}]}";

    private UUID seedLanguage() {
        UUID en = data.language("en", "English", true);
        UUID lesson = data.lesson(en, 1, "Lesson 1");
        data.exerciseDuelEligible(lesson, "TRUE_FALSE", 1,
                ExerciseSamples.TRUE_FALSE.payloadJson(), ExerciseSamples.TRUE_FALSE.solutionJson(), 10);
        data.exerciseDuelEligible(lesson, "MULTIPLE_CHOICE", 2,
                ExerciseSamples.MULTIPLE_CHOICE.payloadJson(), ExerciseSamples.MULTIPLE_CHOICE.solutionJson(), 10);
        data.exerciseDuelEligible(lesson, "MATCHING", 3,
                ExerciseSamples.MATCHING.payloadJson(), ExerciseSamples.MATCHING.solutionJson(), 10);
        return en;
    }

    private record Completion(UUID userId, UUID challengeId) {
    }

    /**
     * Teljesíti a napi kihívást (mindhárom feladat helyesen beküldve) és
     * visszaadja a challenge id-t + user id-t.
     */
    private Completion completeChallenge(UUID userId, UUID languageId) {
        DailyChallengeTodayResponse today = dailyChallengeService.getToday(userId, languageId);
        UUID challengeId = today.challengeId();
        for (var ex : today.exercises()) {
            String answer = switch (ex.type()) {
                case TRUE_FALSE -> TRUE_FALSE_CORRECT;
                case MULTIPLE_CHOICE -> MULTIPLE_CHOICE_CORRECT;
                case MATCHING -> MATCHING_CORRECT;
                default -> throw new IllegalStateException("unexpected type " + ex.type());
            };
            dailyChallengeService.submit(userId, languageId, ex.exerciseId(),
                    UUID.randomUUID().toString(), json(answer));
        }
        return new Completion(userId, challengeId);
    }

    @Test
    void completionArmsPendingBonusWithDeterministicKey() {
        UUID userId = UUID.randomUUID();
        UUID en = seedLanguage();

        Completion completion = completeChallenge(userId, en);

        UserDailyProgress progress = progressRepository
                .findById(new UserDailyProgressId(userId, completion.challengeId())).orElseThrow();
        assertThat(progress.getStatus()).isEqualTo(ProgressStatus.COMPLETED);
        assertThat(progress.getBonusAwardStatus()).isEqualTo(PointAwardStatus.PENDING);
        assertThat(progress.getBonusIdempotencyKey())
                .isEqualTo("daily:" + userId + ":" + businessClock.today() + ":bonus");
        assertThat(progress.isBonusAwarded()).isFalse();
    }

    @Test
    void processingBonusOnceSendsTwentyPointsAndMarksAwarded() {
        UUID userId = UUID.randomUUID();
        UUID en = seedLanguage();
        when(identityPointsPort.award(any())).thenReturn(AwardPointsResult.AWARDED);

        Completion completion = completeChallenge(userId, en);
        pointAwardService.processDailyBonus(userId, completion.challengeId());

        ArgumentCaptor<AwardPointsCommand> captor = ArgumentCaptor.forClass(AwardPointsCommand.class);
        org.mockito.Mockito.verify(identityPointsPort, org.mockito.Mockito.times(1)).award(captor.capture());
        AwardPointsCommand sent = captor.getValue();
        assertThat(sent.points()).isEqualTo(20);
        assertThat(sent.sourceType()).isEqualTo("DAILY_BONUS");
        assertThat(sent.sourceId()).isEqualTo(completion.challengeId());
        assertThat(sent.idempotencyKey())
                .isEqualTo("daily:" + userId + ":" + businessClock.today() + ":bonus");

        UserDailyProgress progress = progressRepository
                .findById(new UserDailyProgressId(userId, completion.challengeId())).orElseThrow();
        assertThat(progress.getBonusAwardStatus()).isEqualTo(PointAwardStatus.AWARDED);
        assertThat(progress.isBonusAwarded()).isTrue();
    }

    @Test
    void repeatedProcessingNeverDoubleAwards() {
        UUID userId = UUID.randomUUID();
        UUID en = seedLanguage();
        when(identityPointsPort.award(any())).thenReturn(AwardPointsResult.AWARDED);

        Completion completion = completeChallenge(userId, en);
        pointAwardService.processDailyBonus(userId, completion.challengeId());
        // A második feldolgozás már nem küld: AWARDED állapot nem esedékes.
        pointAwardService.processDailyBonus(userId, completion.challengeId());

        org.mockito.Mockito.verify(identityPointsPort, org.mockito.Mockito.times(1)).award(any());
    }

    @Test
    void belowTwoCorrectDoesNotCompleteNorArmBonus() {
        UUID userId = UUID.randomUUID();
        UUID en = seedLanguage();

        DailyChallengeTodayResponse today = dailyChallengeService.getToday(userId, en);
        UUID challengeId = today.challengeId();
        // Mindhárom beküldve, de csak egy (TRUE_FALSE) helyes -> nem teljesül.
        for (var ex : today.exercises()) {
            String answer = switch (ex.type()) {
                case TRUE_FALSE -> TRUE_FALSE_CORRECT;
                case MULTIPLE_CHOICE -> "{\"selectedOptionIds\": [\"a\"]}";
                case MATCHING -> "{\"pairs\": [{\"leftId\": \"l1\", \"rightId\": \"r2\"}]}";
                default -> throw new IllegalStateException("unexpected type " + ex.type());
            };
            dailyChallengeService.submit(userId, en, ex.exerciseId(),
                    UUID.randomUUID().toString(), json(answer));
        }

        UserDailyProgress progress = progressRepository
                .findById(new UserDailyProgressId(userId, challengeId)).orElseThrow();
        assertThat(progress.getStatus()).isNotEqualTo(ProgressStatus.COMPLETED);
        assertThat(progress.getBonusAwardStatus()).isEqualTo(PointAwardStatus.NOT_APPLICABLE);
        assertThat(progress.getBonusIdempotencyKey()).isNull();

        List<UserDailyProgress> due = progressRepository.findDueDailyBonuses(
                java.time.Instant.now().plusSeconds(3600), org.springframework.data.domain.Limit.of(100));
        assertThat(due).noneMatch(p -> p.getId().getUserId().equals(userId));
    }
}
