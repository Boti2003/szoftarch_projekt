package hu.bme.szoftarch.learning.domain.exercise;

/**
 * A felhasználói válasz nem a feladattípushoz tartozó {@link UserAnswer}
 * recordra érkezett (pl. feleletválasztós evaluatornak párosítós válasz jutott).
 *
 * <p>Kezelt validációs hiba, nem szerverhiba: az U4 réteg ezt
 * {@code problem+json} 400-as válaszra fordítja. A kivétel üzenete SOHA nem
 * tartalmazza a helyes megoldást (TERV 5.2).</p>
 */
public class AnswerMismatchException extends RuntimeException {

    public AnswerMismatchException(String message) {
        super(message);
    }
}
