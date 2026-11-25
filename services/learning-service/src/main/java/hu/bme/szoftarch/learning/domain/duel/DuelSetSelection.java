package hu.bme.szoftarch.learning.domain.duel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;

/**
 * A párbaj feladatkészletének determinisztikus kiválasztása (TERV 5.5). Egy
 * adott párbaj UUID-ra ({@code duelId}) a jelölt (engedélyezett, duel-eligible,
 * auto-kiértékelhető) feladatokból MINDIG ugyanazt az 5 feladatot adja,
 * ugyanabban a sorrendben — függetlenül a bemeneti sorrendtől és a hívások
 * számától. Spring-mentes domain konstrukció, így egységtesztelhető.
 *
 * <p>A kiválasztott 5 feladat a létrehozáskor a {@code duel_set_exercises}
 * táblába MENTŐDIK, így a készlet rögzül: mindkét résztvevő pontosan ugyanazt az
 * 5 feladatot kapja ugyanabban a sorrendben, akkor is, ha a jelölthalmaz később
 * változik.</p>
 *
 * <p>Az algoritmus: a jelölteket azonosító szerint stabilan rendezi (a bemeneti
 * sorrendtől függetlenül), majd a {@code duelId} stringjéből vezérelt,
 * determinisztikus részleges Fisher–Yates keveréssel választja ki az első 5
 * elemet. A seed a {@link String#hashCode()}-ra épül, ami egy adott stringre
 * JVM-ek között is azonos, így a kiválasztás stabil.</p>
 */
public final class DuelSetSelection {

    /** A párbaj feladatkészletének fix mérete (TERV 5.5). */
    public static final int DUEL_SET_SIZE = 5;

    private DuelSetSelection() {
    }

    /**
     * Kiválaszt pontosan 5 feladatot a jelölt feladatokból determinisztikusan.
     *
     * @param eligibleExerciseIds a jelölt feladatok azonosítói (tetszőleges
     *     sorrendben; a metódus stabilan rendezi és duplikátummentesíti)
     * @param duelId              a Social párbaj UUID-ja (a seed forrása)
     * @return pontosan {@link #DUEL_SET_SIZE} feladat azonosítója determinisztikus
     *     sorrendben
     * @throws IllegalStateException ha a jelölthalmazban kevesebb mint
     *     {@link #DUEL_SET_SIZE} egyedi feladat van (nem lehet teljes készletet
     *     gyártani, így a „mindkét fél ugyanazt az 5 feladatot kapja" invariáns
     *     nem tartható)
     */
    public static List<UUID> select(List<UUID> eligibleExerciseIds, UUID duelId) {
        Objects.requireNonNull(eligibleExerciseIds, "eligibleExerciseIds");
        Objects.requireNonNull(duelId, "duelId");

        // Stabil kiindulás: azonosító szerint rendezett, duplikátummentes lista,
        // hogy a bemeneti sorrend ne befolyásolja az eredményt.
        List<UUID> pool = eligibleExerciseIds.stream()
                .distinct()
                .sorted()
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));

        if (pool.size() < DUEL_SET_SIZE) {
            throw new IllegalStateException(
                    "Not enough eligible exercises for a duel set: need " + DUEL_SET_SIZE
                            + ", have " + pool.size());
        }

        long seed = duelId.toString().hashCode();
        Random random = new Random(seed);

        // Determinisztikus részleges Fisher–Yates: az első DUEL_SET_SIZE pozíciót
        // rögzítjük a seed vezérelte sorsolással. Ugyanaz a duelId → ugyanaz az 5
        // elem, ugyanabban a sorrendben.
        for (int i = 0; i < DUEL_SET_SIZE; i++) {
            int j = i + random.nextInt(pool.size() - i);
            UUID tmp = pool.get(i);
            pool.set(i, pool.get(j));
            pool.set(j, tmp);
        }
        return List.copyOf(pool.subList(0, DUEL_SET_SIZE));
    }
}
