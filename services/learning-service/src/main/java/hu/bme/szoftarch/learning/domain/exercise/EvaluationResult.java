package hu.bme.szoftarch.learning.domain.exercise;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Egy automatikus kiértékelés eredménye (U3). Tiszta domain érték-objektum,
 * Spring-függőség nélkül.
 *
 * <p><strong>Kritikus:</strong> a {@code feedback} SOHA nem tartalmazhatja a
 * helyes megoldást (TERV 5.2) — hibás ágon sem. A helyes választ a kliens más
 * csatornán kaphatja meg, nem innen.</p>
 *
 * @param scoreRatio az elért pontarány a {@code [0, 1]} intervallumban,
 *     NUMERIC(5,4) pontossággal (4 tizedesjegyre kerekítve)
 * @param correct igaz, ha a válasz teljesen helyes
 * @param feedback rövid, megoldást nem szivárogtató visszajelzés
 */
public record EvaluationResult(BigDecimal scoreRatio, boolean correct, String feedback) {

    /**
     * A {@code score_ratio} oszlop pontossága a TERV 6.2 szerint: NUMERIC(5,4).
     */
    public static final int SCALE = 4;

    public EvaluationResult {
        Objects.requireNonNull(scoreRatio, "scoreRatio");
        Objects.requireNonNull(feedback, "feedback");
        scoreRatio = scoreRatio.setScale(SCALE, RoundingMode.HALF_UP);
        if (scoreRatio.compareTo(BigDecimal.ZERO) < 0 || scoreRatio.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("scoreRatio must be in [0, 1], was " + scoreRatio);
        }
    }

    /**
     * Teljesen helyes eredmény: {@code score_ratio = 1.0}, {@code correct = true}.
     */
    public static EvaluationResult correct(String feedback) {
        return new EvaluationResult(BigDecimal.ONE, true, feedback);
    }

    /**
     * Teljesen hibás eredmény: {@code score_ratio = 0.0}, {@code correct = false}.
     */
    public static EvaluationResult incorrect(String feedback) {
        return new EvaluationResult(BigDecimal.ZERO, false, feedback);
    }

    /**
     * Részleges eredmény a megadott aránnyal. A {@code correct} csak akkor igaz,
     * ha az arány pontosan {@code 1}.
     *
     * @param ratio    a helyes rész aránya a {@code [0, 1]} intervallumban
     * @param feedback megoldást nem szivárogtató visszajelzés
     */
    public static EvaluationResult partial(BigDecimal ratio, String feedback) {
        BigDecimal scaled = ratio.setScale(SCALE, RoundingMode.HALF_UP);
        boolean full = scaled.compareTo(BigDecimal.ONE) == 0;
        return new EvaluationResult(scaled, full, feedback);
    }
}
