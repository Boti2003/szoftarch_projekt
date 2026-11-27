package hu.bme.szoftarch.learning.api;

/**
 * Hibás kérés (HTTP 400) a kliens bemenete miatt. A globális hibakezelő
 * problem+json válasszá alakítja. Az üzenet SOHA nem tartalmaz megoldást vagy
 * titkot.
 */
public class BadRequestException extends RuntimeException {

    private final String code;

    public BadRequestException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
