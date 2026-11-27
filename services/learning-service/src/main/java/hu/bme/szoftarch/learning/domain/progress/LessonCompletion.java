package hu.bme.szoftarch.learning.domain.progress;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Egy lecke teljesítésének súlyozott számítása (TERV 5.3). Tiszta domain
 * érték-objektum, Spring-függőség nélkül.
 *
 * <p>A teljesítés feltétele: a lecke MINDEN kötelező feladatát megpróbálta a
 * felhasználó, és a {@code max_points}-tal súlyozott legjobb pontarányok átlaga
 * eléri a 70%-ot. Az opcionális feladatok
 * ({@code required_for_completion = false}) nem szerepelnek a súlyozásban:
 * sem a {@code required} listában, sem a {@code bestRatios} map-ben, ezért sem
 * a számlálót, sem a nevezőt nem befolyásolják.</p>
 */
public final class LessonCompletion {

    /**
     * A teljesítési küszöb: a súlyozott legjobb pontarány ezt elérve számít
     * teljesítettnek (TERV 5.3).
     */
    public static final BigDecimal COMPLETION_THRESHOLD = new BigDecimal("0.70");

    private static final int SCALE = 4;

    private final List<RequiredExercise> required;
    private final Map<UUID, BigDecimal> bestRatios;

    /**
     * @param required   a kötelező feladatok (azonosító + súly = {@code max_points})
     * @param bestRatios a felhasználó legjobb pontaránya feladatonként;
     *     a hiányzó kulcs azt jelenti, hogy a feladatot még nem próbálta
     */
    public LessonCompletion(List<RequiredExercise> required, Map<UUID, BigDecimal> bestRatios) {
        this.required = List.copyOf(required);
        this.bestRatios = Map.copyOf(bestRatios);
    }

    /**
     * @return igaz, ha minden kötelező feladatnak van legjobb pontaránya
     *     (azaz a felhasználó mindet megpróbálta)
     */
    public boolean allRequiredAttempted() {
        return required.stream().allMatch(ex -> bestRatios.containsKey(ex.exerciseId()));
    }

    /**
     * @return a kötelező feladatok {@code max_points}-tal súlyozott legjobb
     *     pontarányainak átlaga; nevező 0 esetén (nincs kötelező feladat vagy
     *     összsúly 0) {@link BigDecimal#ZERO}
     */
    public BigDecimal weightedBestRatio() {
        BigDecimal numerator = BigDecimal.ZERO;
        BigDecimal denominator = BigDecimal.ZERO;
        for (RequiredExercise ex : required) {
            BigDecimal weight = BigDecimal.valueOf(ex.weight());
            BigDecimal best = bestRatios.getOrDefault(ex.exerciseId(), BigDecimal.ZERO);
            numerator = numerator.add(weight.multiply(best));
            denominator = denominator.add(weight);
        }
        if (denominator.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return numerator.divide(denominator, SCALE, RoundingMode.HALF_UP);
    }

    /**
     * @return igaz, ha minden kötelező feladat megpróbált ÉS a súlyozott legjobb
     *     pontarány eléri a {@link #COMPLETION_THRESHOLD}-t
     */
    public boolean isCompleted() {
        return allRequiredAttempted() && weightedBestRatio().compareTo(COMPLETION_THRESHOLD) >= 0;
    }

    /**
     * Egy kötelező feladat a súlyozott számításhoz.
     *
     * @param exerciseId a feladat azonosítója
     * @param weight     a súly ({@code max_points}); nem negatív
     */
    public record RequiredExercise(UUID exerciseId, int weight) {
        public RequiredExercise {
            Objects.requireNonNull(exerciseId, "exerciseId");
        }
    }
}
