package hu.bme.szoftarch.learning.domain.exercise;

/**
 * Jelölő a feladattípusonkénti {@code payload} recordokhoz (TERV 6.2). A
 * payload a feladat prezentálásához szükséges adat (opciók, tokenek, párok,
 * scenario konfiguráció); soha nem tartalmaz helyes megoldást.
 *
 * <p>A konkrét recordok tiszta Java + Bean Validation annotációk; a domain
 * réteg nem függ Springtől.</p>
 */
public sealed interface ExercisePayload permits
        MultipleChoicePayload,
        TrueFalsePayload,
        MatchingPayload,
        OddOneOutPayload,
        ErrorFindingPayload,
        TranslationPayload,
        ScenarioPayload {
}
