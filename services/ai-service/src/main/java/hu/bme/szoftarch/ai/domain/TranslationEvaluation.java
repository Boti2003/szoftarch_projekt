package hu.bme.szoftarch.ai.domain;

/**
 * Fordítás-értékelés strukturált eredménye.
 *
 * @param score    0.0–1.0 közötti hasonlósági/helyességi pontszám
 * @param accepted elfogadva-e (score &gt;= elfogadási küszöb)
 * @param feedback rövid, a tanulónak szóló szöveges visszajelzés
 */
public record TranslationEvaluation(double score, boolean accepted, String feedback) {

    /** A fordítás elfogadásának küszöbe. */
    public static final double ACCEPTANCE_THRESHOLD = 0.70;

    public TranslationEvaluation {
        if (score < 0.0 || score > 1.0) {
            throw new IllegalArgumentException("score 0.0 és 1.0 közötti kell legyen, kapott: " + score);
        }
        if (feedback == null) {
            feedback = "";
        }
    }

    /**
     * Értékelés a score alapján: az elfogadás a küszöbhöz igazodik,
     * így a hívónak nem kell külön eldöntenie.
     */
    public static TranslationEvaluation of(double score, String feedback) {
        return new TranslationEvaluation(score, score >= ACCEPTANCE_THRESHOLD, feedback);
    }
}
