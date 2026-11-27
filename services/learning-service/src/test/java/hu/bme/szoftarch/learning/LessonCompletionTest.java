package hu.bme.szoftarch.learning;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import hu.bme.szoftarch.learning.domain.progress.LessonCompletion;
import hu.bme.szoftarch.learning.domain.progress.LessonCompletion.RequiredExercise;

import org.junit.jupiter.api.Test;

/**
 * A súlyozott 70%-os teljesítési szabály tiszta unit tesztje (TERV 5.3) — DB
 * nélkül. Lefedi a pontosan 70%-os határt, a határ alatti esetet, a hiányzó
 * kötelező beküldést és az opcionális feladat kizárását.
 */
class LessonCompletionTest {

    @Test
    void exactlySeventyPercentIsCompleted() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        // Egyenlő súlyok, átlag pontosan 0.70.
        LessonCompletion completion = new LessonCompletion(
                List.of(new RequiredExercise(a, 10), new RequiredExercise(b, 10)),
                Map.of(a, new BigDecimal("0.6000"), b, new BigDecimal("0.8000")));

        assertThat(completion.weightedBestRatio()).isEqualByComparingTo("0.7000");
        assertThat(completion.isCompleted()).isTrue();
    }

    @Test
    void justBelowSeventyPercentIsNotCompleted() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        LessonCompletion completion = new LessonCompletion(
                List.of(new RequiredExercise(a, 10), new RequiredExercise(b, 10)),
                Map.of(a, new BigDecimal("0.6000"), b, new BigDecimal("0.7998")));

        assertThat(completion.weightedBestRatio()).isLessThan(new BigDecimal("0.70"));
        assertThat(completion.isCompleted()).isFalse();
    }

    @Test
    void missingRequiredAttemptBlocksCompletion() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        // 'b'-t nem próbálta: nem minden kötelező megpróbált, nem teljesíthető,
        // még ha a súlyozott arány formálisan el is érné a küszöböt.
        LessonCompletion completion = new LessonCompletion(
                List.of(new RequiredExercise(a, 10), new RequiredExercise(b, 10)),
                Map.of(a, new BigDecimal("1.0000")));

        assertThat(completion.allRequiredAttempted()).isFalse();
        assertThat(completion.isCompleted()).isFalse();
    }

    @Test
    void optionalExerciseExcludedFromWeighting() {
        UUID required = UUID.randomUUID();
        UUID optional = UUID.randomUUID();
        // Az opcionális feladat nem szerepel sem a required listában, sem a
        // bestRatios-ban: a súlyozás csak a kötelező feladatra épül.
        LessonCompletion completion = new LessonCompletion(
                List.of(new RequiredExercise(required, 10)),
                Map.of(required, new BigDecimal("0.7000"), optional, new BigDecimal("0.0000")));

        assertThat(completion.weightedBestRatio()).isEqualByComparingTo("0.7000");
        assertThat(completion.isCompleted()).isTrue();
    }

    @Test
    void noRequiredExercisesYieldsZeroAndNotCompleted() {
        LessonCompletion completion = new LessonCompletion(List.of(), Map.of());

        assertThat(completion.weightedBestRatio()).isEqualByComparingTo("0");
        assertThat(completion.isCompleted()).isFalse();
    }
}
