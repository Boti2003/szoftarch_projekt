package hu.bme.szoftarch.learning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import hu.bme.szoftarch.learning.application.PointAwardService;
import hu.bme.szoftarch.learning.domain.model.ExerciseAttempt;
import hu.bme.szoftarch.learning.domain.model.PointAwardStatus;
import hu.bme.szoftarch.learning.domain.port.AwardPointsCommand;
import hu.bme.szoftarch.learning.domain.port.AwardPointsResult;
import hu.bme.szoftarch.learning.domain.port.IdentityPointsPort;
import hu.bme.szoftarch.learning.domain.repository.ExerciseAttemptRepository;
import hu.bme.szoftarch.learning.support.ExerciseSamples;
import hu.bme.szoftarch.learning.support.TestLearningData;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * A pontjóváírás életciklusának és retry-korlátjának tesztje (TERV B – U5, 7.8)
 * H2-n, mockolt {@link IdentityPointsPort}-tal, hogy a kimeneteket determinisztikusan
 * vezéreljük és a kiküldött idempotenciakulcsot ellenőrizzük. A valódi HTTP-leképezést
 * az {@code IdentityPointsClientWireMockTest} fedi.
 */
@SpringBootTest
@ActiveProfiles("test")
class PointAwardLifecycleTest {

    private final PointAwardService pointAwardService;
    private final ExerciseAttemptRepository attemptRepository;
    private final TestLearningData data;

    @MockitoBean
    private IdentityPointsPort identityPointsPort;

    @Autowired
    PointAwardLifecycleTest(PointAwardService pointAwardService,
            ExerciseAttemptRepository attemptRepository, TestLearningData data) {
        this.pointAwardService = pointAwardService;
        this.attemptRepository = attemptRepository;
        this.data = data;
    }

    @BeforeEach
    void cleanUp() {
        data.deleteAll();
    }

    private UUID seedPendingAttempt(UUID userId) {
        UUID en = data.language("en", "English", true);
        UUID lesson = data.lesson(en, 1, "Lesson 1");
        UUID exercise = data.exerciseScorable(lesson, "TRUE_FALSE", 1,
                ExerciseSamples.TRUE_FALSE.payloadJson(), ExerciseSamples.TRUE_FALSE.solutionJson(),
                10, true);
        String idemKey = "exercise:" + userId + ":" + exercise + ":first-success";
        return data.attempt(userId, exercise, lesson, "1.0000", true,
                UUID.randomUUID().toString(), "PENDING", idemKey);
    }

    @Test
    void pendingSendSuccessMarksAwarded() {
        UUID userId = UUID.randomUUID();
        UUID attemptId = seedPendingAttempt(userId);
        when(identityPointsPort.award(any())).thenReturn(AwardPointsResult.AWARDED);

        pointAwardService.processAttempt(attemptId);

        ExerciseAttempt stored = attemptRepository.findById(attemptId).orElseThrow();
        assertThat(stored.getPointAwardStatus()).isEqualTo(PointAwardStatus.AWARDED);
        assertThat(stored.getNextRetryAt()).isNull();
    }

    @Test
    void retryableErrorMarksFailedAndSetsNextRetryWithSameKey() {
        UUID userId = UUID.randomUUID();
        UUID attemptId = seedPendingAttempt(userId);
        String idemKey = attemptRepository.findById(attemptId).orElseThrow().getPointIdempotencyKey();
        when(identityPointsPort.award(any())).thenReturn(AwardPointsResult.RETRYABLE_ERROR);

        // 1. kudarc (PENDING -> FAILED_RETRYABLE), majd 2. kudarc (retry).
        pointAwardService.processAttempt(attemptId);
        Instant afterFirst = Instant.now();
        pointAwardService.processAttempt(attemptId);

        ExerciseAttempt stored = attemptRepository.findById(attemptId).orElseThrow();
        assertThat(stored.getPointAwardStatus()).isEqualTo(PointAwardStatus.FAILED_RETRYABLE);
        assertThat(stored.getPointAttemptCount()).isEqualTo(2);
        assertThat(stored.getNextRetryAt()).isAfter(afterFirst);

        // A retry UGYANAZZAL az idempotenciakulccsal megy mindkét próbálkozásnál.
        ArgumentCaptor<AwardPointsCommand> captor = ArgumentCaptor.forClass(AwardPointsCommand.class);
        org.mockito.Mockito.verify(identityPointsPort, org.mockito.Mockito.times(2)).award(captor.capture());
        List<AwardPointsCommand> sent = captor.getAllValues();
        assertThat(sent).allSatisfy(c -> assertThat(c.idempotencyKey()).isEqualTo(idemKey));
    }

    @Test
    void sameKeyNeverDoubleAwards() {
        UUID userId = UUID.randomUUID();
        UUID attemptId = seedPendingAttempt(userId);
        when(identityPointsPort.award(any())).thenReturn(AwardPointsResult.AWARDED);

        // Első feldolgozás jóváír; a második már nem küld (AWARDED nem esedékes).
        pointAwardService.processAttempt(attemptId);
        pointAwardService.processAttempt(attemptId);

        org.mockito.Mockito.verify(identityPointsPort, org.mockito.Mockito.times(1)).award(any());
        assertThat(attemptRepository.findById(attemptId).orElseThrow().getPointAwardStatus())
                .isEqualTo(PointAwardStatus.AWARDED);
    }

    @Test
    void thirdFailureLeavesBoundedAndIsNotReselected() {
        UUID userId = UUID.randomUUID();
        UUID attemptId = seedPendingAttempt(userId);
        when(identityPointsPort.award(any())).thenReturn(AwardPointsResult.RETRYABLE_ERROR);

        // Három kudarc (1/5/15 perc időzítés). A számláló eléri a 3-at, ezután a
        // poller lekérdezése (point_attempt_count < 3) többé nem választja ki,
        // így az automatikus retry korlátozott (nincs vég nélküli próbálkozás).
        pointAwardService.processAttempt(attemptId);
        pointAwardService.processAttempt(attemptId);
        pointAwardService.processAttempt(attemptId);

        ExerciseAttempt stored = attemptRepository.findById(attemptId).orElseThrow();
        assertThat(stored.getPointAttemptCount()).isEqualTo(3);
        assertThat(stored.getPointAwardStatus()).isEqualTo(PointAwardStatus.FAILED_RETRYABLE);

        // A poller lekérdezése már nem választja ki, akármilyen messze is a jelen.
        List<ExerciseAttempt> due = attemptRepository.findDuePointAwards(
                Instant.now().plusSeconds(3600), org.springframework.data.domain.Limit.of(100));
        assertThat(due).noneMatch(a -> a.getId().equals(attemptId));
    }

    @Test
    void nonRetryableErrorIsNotReselected() {
        UUID userId = UUID.randomUUID();
        UUID attemptId = seedPendingAttempt(userId);
        when(identityPointsPort.award(any())).thenReturn(AwardPointsResult.NON_RETRYABLE_ERROR);

        pointAwardService.processAttempt(attemptId);

        ExerciseAttempt stored = attemptRepository.findById(attemptId).orElseThrow();
        assertThat(stored.getPointAwardStatus()).isEqualTo(PointAwardStatus.FAILED_RETRYABLE);
        assertThat(stored.getNextRetryAt()).isNull();
        List<ExerciseAttempt> due = attemptRepository.findDuePointAwards(
                Instant.now().plusSeconds(3600), org.springframework.data.domain.Limit.of(100));
        assertThat(due).noneMatch(a -> a.getId().equals(attemptId));
    }
}
