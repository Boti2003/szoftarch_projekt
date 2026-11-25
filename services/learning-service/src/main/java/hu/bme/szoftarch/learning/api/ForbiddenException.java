package hu.bme.szoftarch.learning.api;

/**
 * Tiltott hozzáférés (HTTP 403): a hitelesített felhasználó nem fér hozzá az
 * erőforráshoz (pl. más tulajdonos AI-munkamenete, TERV 5.2). A globális
 * hibakezelő problem+json válasszá alakítja; az üzenet SOHA nem tartalmaz
 * titkot vagy megoldást.
 */
public class ForbiddenException extends RuntimeException {

    private final String code;

    public ForbiddenException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
