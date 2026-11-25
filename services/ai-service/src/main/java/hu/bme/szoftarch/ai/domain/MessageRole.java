package hu.bme.szoftarch.ai.domain;

/**
 * Egy beszélgetési üzenet szerepe.
 */
public enum MessageRole {
    /** A szerveroldali rendszerprompt (a session kontextusa). */
    SYSTEM,
    /** A tanuló üzenete. */
    USER,
    /** A nyelvi modell válasza. */
    ASSISTANT
}
