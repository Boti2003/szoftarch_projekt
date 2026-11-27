package hu.bme.szoftarch.learning.domain.port;

/**
 * Az AI-függőség sikertelen vagy elérhetetlen (timeout / 5xx / hiányzó vagy
 * hibás válasz-body). Kezelt, egyértelmű jelzés — nem nyers
 * {@link RuntimeException} —, hogy a hívó egységesen kezelhesse az AI-oldali
 * hibát (TERV B – U8), a {@code NotAutoEvaluableException} mintájára.
 */
public class AiEvaluationException extends RuntimeException {

    public AiEvaluationException(String message) {
        super(message);
    }

    public AiEvaluationException(String message, Throwable cause) {
        super(message, cause);
    }
}
