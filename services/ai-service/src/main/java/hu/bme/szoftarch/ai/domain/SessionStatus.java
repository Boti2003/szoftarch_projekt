package hu.bme.szoftarch.ai.domain;

/**
 * Egy szituáció-session életciklus-állapota.
 */
public enum SessionStatus {
    /** Nyitott, további felhasználói fordulók fogadhatók. */
    OPEN,
    /** Lezárt, záró értékeléssel; nem folytatható. */
    COMPLETED
}
