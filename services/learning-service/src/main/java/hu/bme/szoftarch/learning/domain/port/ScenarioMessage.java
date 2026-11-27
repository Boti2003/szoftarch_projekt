package hu.bme.szoftarch.learning.domain.port;

/**
 * Az AI válasza egy párbeszédes munkamenet egy körében (TERV B – U8). Spring-
 * mentes domain érték.
 *
 * @param aiMessage az AI válaszüzenete
 * @param turnCount a munkamenet körszáma a válasz után
 */
public record ScenarioMessage(String aiMessage, int turnCount) {
}
