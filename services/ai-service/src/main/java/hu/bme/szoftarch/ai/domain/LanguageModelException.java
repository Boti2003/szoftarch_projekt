package hu.bme.szoftarch.ai.domain;

/**
 * Akkor dobódik, ha a nyelvi modell adapter nem tud érvényes értékelést adni
 * (pl. timeout, hibás vagy sémán kívüli kimenet a külső providernél).
 */
public class LanguageModelException extends RuntimeException {

    public LanguageModelException(String message) {
        super(message);
    }

    public LanguageModelException(String message, Throwable cause) {
        super(message, cause);
    }
}
