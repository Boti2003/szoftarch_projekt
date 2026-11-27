package hu.bme.szoftarch.learning.domain.port;

import java.util.UUID;

/**
 * Üzenetküldés egy futó párbeszédes munkamenetbe (TERV B – U8, Adapter minta
 * bemenete). Spring- és HTTP-mentes domain érték. KIZÁRÓLAG a munkamenet
 * azonosítóját és a felhasználói szöveget hordozza (nincs e-mail/felhasználónév/
 * token).
 *
 * @param aiSessionId az AI-oldali munkamenet azonosítója
 * @param userMessage a felhasználó üzenete
 */
public record SendMessageCommand(UUID aiSessionId, String userMessage) {
}
