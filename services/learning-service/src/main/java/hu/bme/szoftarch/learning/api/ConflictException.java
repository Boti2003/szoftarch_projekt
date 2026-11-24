package hu.bme.szoftarch.learning.api;

/**
 * Üzleti konfliktus (HTTP 409). A globális hibakezelő problem+json válasszá
 * alakítja.
 */
public class ConflictException extends RuntimeException {

    private final String code;

    public ConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
