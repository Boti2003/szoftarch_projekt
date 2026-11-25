package hu.bme.szoftarch.ai.domain;

import java.util.UUID;

/**
 * Egy perzisztált beszélgetési üzenet a session-ön belül.
 *
 * @param id         az üzenet azonosítója
 * @param sequenceNo sorszám a session-ön belül (0-tól)
 * @param role       ki küldte
 * @param content    az üzenet szövege
 */
public record ScenarioMessage(UUID id, int sequenceNo, MessageRole role, String content) {

    public ScenarioMessage {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content kötelező");
        }
    }
}
