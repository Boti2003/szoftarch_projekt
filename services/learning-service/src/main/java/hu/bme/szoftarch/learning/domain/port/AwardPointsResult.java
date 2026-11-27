package hu.bme.szoftarch.learning.domain.port;

/**
 * A pontjóváírás kimenetele (TERV B – U5, 7.8). A learning NEM tárol Identity-
 * adatot (pont/szint), csak a saját attempt státuszát vezeti e kimenet alapján.
 */
public enum AwardPointsResult {

    /** 2xx: a pont jóváírva (vagy az idempotens ismétlés az eredetit adta vissza). */
    AWARDED,

    /** 5xx / timeout / hálózati hiba: ugyanazzal a kulccsal újrapróbálható. */
    RETRYABLE_ERROR,

    /** 4xx (pl. validáció): nem újrapróbálható, további próbálkozás értelmetlen. */
    NON_RETRYABLE_ERROR
}
