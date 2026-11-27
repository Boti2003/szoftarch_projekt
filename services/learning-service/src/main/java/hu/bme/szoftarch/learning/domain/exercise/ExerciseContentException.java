package hu.bme.szoftarch.learning.domain.exercise;

/**
 * A tárolt {@code payload}/{@code solution} JSONB nem deszerializálható a
 * feladattípushoz tartozó recordra, vagy megsérti a Bean Validation
 * szabályokat. Szerveroldali adatintegritási hiba (a seed/írás hibája), nem a
 * kliensé: a hibakezelő egyértelmű, stacktrace nélküli választ ad belőle.
 */
public class ExerciseContentException extends RuntimeException {

    public ExerciseContentException(String message) {
        super(message);
    }

    public ExerciseContentException(String message, Throwable cause) {
        super(message, cause);
    }
}
