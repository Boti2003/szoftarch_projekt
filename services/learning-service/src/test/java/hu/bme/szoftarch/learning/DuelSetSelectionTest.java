package hu.bme.szoftarch.learning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import hu.bme.szoftarch.learning.domain.duel.DuelSetSelection;

import org.junit.jupiter.api.Test;

/**
 * A determinisztikus párbaj-készlet kiválasztás domain tesztje (TERV 5.5). A
 * kiválasztás Spring-mentes, így egységteszttel ellenőrizhető: ugyanaz a
 * {@code duelId} → ugyanaz az 5 feladat, ugyanabban a sorrendben, a bemeneti
 * sorrendtől függetlenül; 5-nél kevesebb jelölt esetén hiba (nem gyártható
 * teljes készlet).
 */
class DuelSetSelectionTest {

    private static List<UUID> pool(int size) {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            ids.add(UUID.randomUUID());
        }
        return ids;
    }

    @Test
    void sameDuelIdYieldsSameFiveInSameOrderOnRepeatedCalls() {
        List<UUID> pool = pool(10);
        UUID duelId = UUID.randomUUID();

        List<UUID> first = DuelSetSelection.select(pool, duelId);
        List<UUID> second = DuelSetSelection.select(pool, duelId);

        assertThat(first).hasSize(DuelSetSelection.DUEL_SET_SIZE);
        assertThat(second).isEqualTo(first);
    }

    @Test
    void bothParticipantsGetIdenticalSelectionRegardlessOfInputOrder() {
        List<UUID> pool = pool(12);
        UUID duelId = UUID.randomUUID();

        // „Első résztvevő" nézőpontja: az eredeti sorrendű jelölthalmaz.
        List<UUID> participantOne = DuelSetSelection.select(pool, duelId);

        // „Második résztvevő" nézőpontja: ugyanaz a halmaz, megkevert bemeneti
        // sorrenddel. A kiválasztás a bemeneti sorrendtől független.
        List<UUID> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled);
        List<UUID> participantTwo = DuelSetSelection.select(shuffled, duelId);

        assertThat(participantTwo).isEqualTo(participantOne);
    }

    @Test
    void differentDuelIdMayDiffer() {
        List<UUID> pool = pool(30);

        List<UUID> base = DuelSetSelection.select(pool, UUID.randomUUID());
        List<UUID> other = DuelSetSelection.select(pool, UUID.randomUUID());

        // Nem garantált a különbség minden seedre, de két véletlen duelId mellett
        // a kiválasztás tipikusan eltér; ha mégsem, a determinizmust a többi teszt
        // fedi. Itt legalább a méret és az érvényesség ellenőrzött.
        assertThat(base).hasSize(DuelSetSelection.DUEL_SET_SIZE);
        assertThat(other).hasSize(DuelSetSelection.DUEL_SET_SIZE);
    }

    @Test
    void poolSmallerThanFiveThrows() {
        List<UUID> pool = pool(4);
        UUID duelId = UUID.randomUUID();

        assertThatThrownBy(() -> DuelSetSelection.select(pool, duelId))
                .isInstanceOf(IllegalStateException.class);
    }
}
