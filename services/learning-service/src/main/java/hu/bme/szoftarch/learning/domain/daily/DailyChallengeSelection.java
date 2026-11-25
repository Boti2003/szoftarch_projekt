package hu.bme.szoftarch.learning.domain.daily;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;

/**
 * A napi kihívás determinisztikus feladat-kiválasztása (TERV 5.4). Egy adott
 * {@code (challengeDate, languageId)} párra MINDIG ugyanazt a (legfeljebb 3)
 * feladatot adja, ugyanabban a sorrendben — függetlenül a felhasználótól és a
 * hívások számától. Spring-mentes domain konstrukció, így egységtesztelhető.
 *
 * <p>Az algoritmus: a jelölt feladatokat azonosító szerint stabilan rendezi (a
 * bemeneti sorrendtől függetlenül), majd a {@code date + ":" + languageId}
 * seedből vezérelt, determinisztikus Fisher–Yates keveréssel választja ki az
 * első (legfeljebb) 3 elemet. A seed csak a {@link String#hashCode()}-ra épül,
 * ami egy adott stringre JVM-ek között is azonos, így a kiválasztás stabil.</p>
 */
public final class DailyChallengeSelection {

    /** A napi kihívás feladatainak kívánt száma (TERV 5.4). */
    public static final int CHALLENGE_SIZE = 3;

    private DailyChallengeSelection() {
    }

    /**
     * Kiválaszt legfeljebb 3 feladatot a jelölt (engedélyezett, duel-eligible,
     * auto-kiértékelhető) feladatokból determinisztikusan.
     *
     * @param eligibleExerciseIds a jelölt feladatok azonosítói (tetszőleges
     *     sorrendben; a metódus stabilan rendezi)
     * @param date                az üzleti nap dátuma
     * @param languageId          a nyelv azonosítója
     * @return legfeljebb 3 feladat azonosítója determinisztikus sorrendben; ha a
     *     jelölthalmazban 3-nál kevesebb van, annyit ad, amennyi van
     */
    public static List<UUID> select(List<UUID> eligibleExerciseIds, LocalDate date, UUID languageId) {
        Objects.requireNonNull(eligibleExerciseIds, "eligibleExerciseIds");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(languageId, "languageId");

        // Stabil kiindulás: azonosító szerint rendezett, duplikátummentes lista,
        // hogy a bemeneti sorrend ne befolyásolja az eredményt.
        List<UUID> pool = eligibleExerciseIds.stream()
                .distinct()
                .sorted()
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));

        if (pool.size() <= CHALLENGE_SIZE) {
            return List.copyOf(pool);
        }

        long seed = seedFor(date, languageId);
        Random random = new Random(seed);

        // Determinisztikus részleges Fisher–Yates: az első CHALLENGE_SIZE pozíciót
        // rögzítjük, a seed vezérelte sorsolással. Ugyanaz a seed → ugyanaz a 3 elem,
        // ugyanabban a sorrendben.
        for (int i = 0; i < CHALLENGE_SIZE; i++) {
            int j = i + random.nextInt(pool.size() - i);
            UUID tmp = pool.get(i);
            pool.set(i, pool.get(j));
            pool.set(j, tmp);
        }
        return List.copyOf(pool.subList(0, CHALLENGE_SIZE));
    }

    /**
     * A {@code date + ":" + languageId} stringből determinisztikus seed. A
     * {@link String#hashCode()} egy adott stringre JVM-ek között is azonos.
     */
    private static long seedFor(LocalDate date, UUID languageId) {
        String key = date.toString() + ":" + languageId;
        return key.hashCode();
    }
}
