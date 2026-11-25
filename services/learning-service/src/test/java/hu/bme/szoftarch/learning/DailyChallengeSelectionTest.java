package hu.bme.szoftarch.learning;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import hu.bme.szoftarch.learning.domain.daily.DailyChallengeSelection;

import org.junit.jupiter.api.Test;

/**
 * A determinisztikus napi kihívás-kiválasztás domain tesztje (TERV 5.4). A
 * kiválasztás Spring-mentes, így egységteszttel ellenőrizhető: ugyanaz a
 * {@code (date, languageId)} → ugyanaz a 3 feladat, ugyanabban a sorrendben,
 * ismételt hívásra és „két felhasználó" nézőpontból is.
 */
class DailyChallengeSelectionTest {

    private static List<UUID> pool(int size) {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            ids.add(UUID.randomUUID());
        }
        return ids;
    }

    @Test
    void sameDateAndLanguageYieldSameThreeInSameOrderOnRepeatedCalls() {
        List<UUID> pool = pool(10);
        LocalDate date = LocalDate.of(2025, 3, 14);
        UUID languageId = UUID.randomUUID();

        List<UUID> first = DailyChallengeSelection.select(pool, date, languageId);
        List<UUID> second = DailyChallengeSelection.select(pool, date, languageId);

        assertThat(first).hasSize(3);
        assertThat(second).isEqualTo(first);
    }

    @Test
    void twoUsersViewpointYieldsIdenticalSelectionRegardlessOfInputOrder() {
        List<UUID> pool = pool(12);
        LocalDate date = LocalDate.of(2025, 3, 14);
        UUID languageId = UUID.randomUUID();

        // „Első felhasználó" nézőpontja: az eredeti sorrendű jelölthalmaz.
        List<UUID> userOne = DailyChallengeSelection.select(pool, date, languageId);

        // „Második felhasználó" nézőpontja: ugyanaz a halmaz, de megkevert
        // bemeneti sorrenddel. A kiválasztás a bemeneti sorrendtől független.
        List<UUID> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled);
        List<UUID> userTwo = DailyChallengeSelection.select(shuffled, date, languageId);

        assertThat(userTwo).isEqualTo(userOne);
    }

    @Test
    void differentDateOrLanguageMayDiffer() {
        List<UUID> pool = pool(30);
        UUID languageId = UUID.randomUUID();

        List<UUID> base = DailyChallengeSelection.select(pool, LocalDate.of(2025, 3, 14), languageId);
        List<UUID> otherDate = DailyChallengeSelection.select(pool, LocalDate.of(2025, 3, 15), languageId);
        List<UUID> otherLanguage = DailyChallengeSelection.select(pool, LocalDate.of(2025, 3, 14),
                UUID.randomUUID());

        // Nem garantált a különbség, de a seed eltér; legalább az egyik eltér.
        assertThat(otherDate.equals(base) && otherLanguage.equals(base)).isFalse();
    }

    @Test
    void poolSmallerThanThreeReturnsAllDeterministically() {
        List<UUID> pool = pool(2);
        LocalDate date = LocalDate.of(2025, 3, 14);
        UUID languageId = UUID.randomUUID();

        List<UUID> selected = DailyChallengeSelection.select(pool, date, languageId);

        assertThat(selected).hasSize(2);
        assertThat(selected).containsExactlyInAnyOrderElementsOf(pool);
    }
}
