package hu.bme.szoftarch.learning.domain.exercise;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Az AI-kiértékelés küszöb- és pontlogikája (TERV B – U8). Tiszta domain
 * segédosztály, Spring-függőség nélkül, egységtesztelhető.
 *
 * <ul>
 *   <li>Fordítás: elfogadott, ha a pontszám {@code >= 0.70}.</li>
 *   <li>Párbeszéd: az átlag a három részpont számtani közepe; teljesítettnek
 *       számít, ha az átlag {@code >= 0.60}.</li>
 *   <li>Párbeszéd pontja: {@code round(átlag * maxPoints)}, legfeljebb 30.</li>
 * </ul>
 *
 * A {@code [0, 1]} arányokat a {@link EvaluationResult#SCALE} 4 tizedesjegyre,
 * {@link RoundingMode#HALF_UP} módon kerekítve adjuk vissza.
 */
public final class AiScoring {

    /** A fordítás elfogadásának küszöbe (TERV 5.2). */
    public static final BigDecimal TRANSLATION_ACCEPT_THRESHOLD = new BigDecimal("0.70");

    /** A párbeszéd teljesítésének küszöbe az átlagra (TERV 5.2). */
    public static final BigDecimal SCENARIO_COMPLETE_THRESHOLD = new BigDecimal("0.60");

    /** A párbeszédért adható pont felső korlátja (TERV 5.4). */
    public static final int SCENARIO_MAX_AWARDED_POINTS = 30;

    private AiScoring() {
    }

    /**
     * @param score a fordítás pontszáma a {@code [0, 1]} intervallumban
     * @return igaz, ha a pontszám eléri a 0.70 küszöböt
     */
    public static boolean translationAccepted(BigDecimal score) {
        return score.compareTo(TRANSLATION_ACCEPT_THRESHOLD) >= 0;
    }

    /**
     * A három részpont számtani közepe {@code [0, 1]} arányban, NUMERIC(5,4)
     * pontossággal.
     *
     * @param comprehensibility az érthetőség részpontja
     * @param grammar           a nyelvtan részpontja
     * @param taskCompletion    a feladatteljesítés részpontja
     * @return az átlag 4 tizedesjegyre kerekítve
     */
    public static BigDecimal scenarioAverage(BigDecimal comprehensibility, BigDecimal grammar,
            BigDecimal taskCompletion) {
        BigDecimal sum = comprehensibility.add(grammar).add(taskCompletion);
        return sum.divide(BigDecimal.valueOf(3), EvaluationResult.SCALE, RoundingMode.HALF_UP);
    }

    /**
     * @param average a részpontok átlaga a {@code [0, 1]} intervallumban
     * @return igaz, ha az átlag eléri a 0.60 küszöböt
     */
    public static boolean scenarioCompleted(BigDecimal average) {
        return average.compareTo(SCENARIO_COMPLETE_THRESHOLD) >= 0;
    }

    /**
     * A párbeszédért járó pont: {@code round(average * maxPoints)}, legfeljebb
     * {@link #SCENARIO_MAX_AWARDED_POINTS}.
     *
     * @param average   a részpontok átlaga a {@code [0, 1]} intervallumban
     * @param maxPoints a feladat maximális pontja
     * @return a jóváírandó pont, 30-ra korlátozva
     */
    public static int scenarioAwardedPoints(BigDecimal average, int maxPoints) {
        long rounded = Math.round(average.doubleValue() * maxPoints);
        return (int) Math.min(rounded, SCENARIO_MAX_AWARDED_POINTS);
    }
}
