package hu.bme.szoftarch.ai.domain;

/**
 * A szituáció megbízható konfigurációja. A hívó service állítja össze,
 * nem a böngésző; a nyelvi modell ebből kapja a szerepét és a célját.
 *
 * @param role           az AI szerepe (pl. "pincér egy étteremben")
 * @param situation      a helyzet leírása
 * @param goal           a tanuló feladata (mit kell elérnie a beszélgetésben)
 * @param targetLanguage a célnyelv kódja (pl. "en")
 * @param openingMessage az AI első, nyitó üzenete
 */
public record ScenarioConfig(
        String role,
        String situation,
        String goal,
        String targetLanguage,
        String openingMessage) {

    public ScenarioConfig {
        if (targetLanguage == null || targetLanguage.isBlank()) {
            throw new IllegalArgumentException("targetLanguage kötelező");
        }
        if (openingMessage == null || openingMessage.isBlank()) {
            throw new IllegalArgumentException("openingMessage kötelező");
        }
    }
}
