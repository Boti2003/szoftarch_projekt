package hu.bme.szoftarch.ai.domain;

/**
 * Egy beszélgetési forduló a nyelvi modellnek átadott kontextusban.
 *
 * @param role    ki küldte (SYSTEM / USER / ASSISTANT)
 * @param content az üzenet szövege
 */
public record ConversationTurn(MessageRole role, String content) {
}
