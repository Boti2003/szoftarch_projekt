package hu.bme.szoftarch.learning.infrastructure.config;

/**
 * A szolgáltatások közötti kéréskövetés headerének neve. A Learning a bejövő
 * {@code X-Correlation-Id}-t propagálja a kimenő hívásokra (TERV megfigyelhetőség).
 */
public final class CorrelationId {

    public static final String HEADER = "X-Correlation-Id";

    private CorrelationId() {
    }
}
