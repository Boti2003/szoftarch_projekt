package hu.bme.szoftarch.social.domain.duel;

/**
 * Outcome of scoring a single duel answer.
 *
 * <p>Authoritative scoring is performed by the Learning service; this value
 * object carries whether the submitted answer was correct and the points
 * awarded for it. Per the scoring rule, {@code awardedPoints} is the
 * exercise's {@code max_points} when {@code correct} is {@code true} and
 * {@code 0} otherwise (design "Winner determination and scoring").
 *
 * @param correct       whether the submitted answer was correct
 * @param awardedPoints points awarded for the answer ({@code 0} when incorrect)
 */
public record ScoreResult(boolean correct, int awardedPoints) {

    public ScoreResult {
        if (awardedPoints < 0) {
            throw new IllegalArgumentException("awardedPoints must not be negative");
        }
    }
}
