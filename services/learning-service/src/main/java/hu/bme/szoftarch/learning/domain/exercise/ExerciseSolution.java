package hu.bme.szoftarch.learning.domain.exercise;

/**
 * Jelölő a feladattípusonkénti {@code solution} recordokhoz (TERV 6.2). A
 * solution a helyes megoldás, amely a kiértékeléshez (U3/U4) kell.
 *
 * <p><strong>Kritikus:</strong> a solution SOHA nem kerül a kliensnek szánt
 * prezentációs válaszba (TERV 5.2). A prezentációs leképezés kizárólag a
 * {@link ExercisePayload}-ból dolgozik, a solutiont nem olvassa.</p>
 */
public sealed interface ExerciseSolution permits
        MultipleChoiceSolution,
        TrueFalseSolution,
        MatchingSolution,
        OddOneOutSolution,
        ErrorFindingSolution {
}
