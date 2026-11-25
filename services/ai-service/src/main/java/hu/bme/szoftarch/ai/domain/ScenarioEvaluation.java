package hu.bme.szoftarch.ai.domain;

/**
 * A szituáció záró értékelése. Három részpontszám 0–1 között; az átlaguk
 * az összpontarány, és a teljesítés a küszöbhöz igazodik.
 *
 * @param comprehensibility érthetőség (0–1)
 * @param grammar           nyelvhelyesség (0–1)
 * @param taskCompletion    a feladat teljesítése (0–1)
 * @param feedback          szöveges visszajelzés a tanulónak
 */
public record ScenarioEvaluation(
        double comprehensibility,
        double grammar,
        double taskCompletion,
        String feedback) {

    /** A szituáció teljesítésének küszöbe (átlagpontarány). */
    public static final double PASS_THRESHOLD = 0.60;

    public ScenarioEvaluation {
        requireRatio("comprehensibility", comprehensibility);
        requireRatio("grammar", grammar);
        requireRatio("taskCompletion", taskCompletion);
        if (feedback == null) {
            feedback = "";
        }
    }

    /** A három részpontszám átlaga. */
    public double overallScore() {
        return (comprehensibility + grammar + taskCompletion) / 3.0;
    }

    /** Teljesített-e: az átlag eléri-e a küszöböt. */
    public boolean passed() {
        return overallScore() >= PASS_THRESHOLD;
    }

    private static void requireRatio(String name, double value) {
        if (value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " 0.0 és 1.0 közötti kell legyen, kapott: " + value);
        }
    }
}
