package hu.bme.szoftarch.social.domain.duel;

import java.util.Objects;
import java.util.UUID;

/**
 * Pure, Spring-free duel scoring and winner-determination rules.
 *
 * <p>Implements the rules in design "Winner determination and scoring":
 * an answer is awarded the exercise's {@code maxPoints} when correct and
 * {@code 0} otherwise, and the winner of a duel is the participant with the
 * higher total score (a draw when the totals are equal). Timing
 * ({@code startedAt}/{@code completedAt}) is never a tiebreaker (design
 * Property 8: Winner determination).
 *
 * <p>This class holds no state; the methods are static rules.
 */
public final class DuelScoring {

    private DuelScoring() {
    }

    /**
     * Scores a single answer: the exercise's {@code maxPoints} are awarded
     * when the answer is correct, otherwise {@code 0}.
     *
     * @param maxPoints the exercise's maximum points (must be {@code >= 0})
     * @param correct   whether the submitted answer was correct
     * @return {@code maxPoints} when {@code correct}, else {@code 0}
     * @throws IllegalArgumentException if {@code maxPoints} is negative
     */
    public static int scoreAnswer(int maxPoints, boolean correct) {
        if (maxPoints < 0) {
            throw new IllegalArgumentException("maxPoints must not be negative");
        }
        return correct ? maxPoints : 0;
    }

    /**
     * Determines the winner of a completed duel from the two participants'
     * total scores.
     *
     * <p>The participant with the strictly higher total wins; equal totals are
     * a draw. Timing is never considered, so the result depends only on the
     * two scores (design Property 8: Winner determination).
     *
     * @param firstParticipantId  id of the first participant
     * @param firstScore          total score of the first participant
     * @param secondParticipantId id of the second participant
     * @param secondScore         total score of the second participant
     * @return the id of the higher-scoring participant, or {@code null} for a
     *         draw when the totals are equal
     * @throws NullPointerException     if either participant id is {@code null}
     * @throws IllegalArgumentException if the two participant ids are equal
     */
    public static UUID determineWinner(UUID firstParticipantId, int firstScore,
                                       UUID secondParticipantId, int secondScore) {
        Objects.requireNonNull(firstParticipantId, "firstParticipantId must not be null");
        Objects.requireNonNull(secondParticipantId, "secondParticipantId must not be null");
        if (firstParticipantId.equals(secondParticipantId)) {
            throw new IllegalArgumentException("duel participants must be distinct");
        }
        if (firstScore > secondScore) {
            return firstParticipantId;
        }
        if (secondScore > firstScore) {
            return secondParticipantId;
        }
        return null;
    }
}
