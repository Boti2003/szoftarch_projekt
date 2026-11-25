package hu.bme.szoftarch.ai.domain;

import java.util.List;

/**
 * Nyelvi modell adapter port.
 *
 * <p>Ez a szerződés választja el az üzleti logikát attól, hogy az értékelés
 * honnan jön. Megvalósíthatja determinisztikus mock (fejlesztés, teszt) vagy
 * valódi, külső nyelvi modellt hívó adapter. A domain és az application réteg
 * kizárólag ezt az interfészt ismeri, a konkrét implementációt nem.
 */
public interface LanguageModelClient {

    /**
     * Egy beküldött fordítás strukturált értékelése.
     *
     * @param request a megbízható kontextus és a tanuló fordítása
     * @return score, elfogadás és szöveges visszajelzés
     * @throws LanguageModelException ha az értékelés nem állítható elő
     */
    TranslationEvaluation evaluateTranslation(TranslationRequest request);

    /**
     * Válasz generálása egy szituáció-beszélgetés következő fordulójára.
     *
     * @param config  a szituáció megbízható konfigurációja
     * @param history az eddigi beszélgetés (régebbitől a legújabbig)
     * @return az AI válaszüzenetének szövege
     * @throws LanguageModelException ha a válasz nem állítható elő
     */
    String continueScenario(ScenarioConfig config, List<ConversationTurn> history);

    /**
     * A szituáció záró értékelése a teljes beszélgetés alapján.
     *
     * @param config  a szituáció megbízható konfigurációja
     * @param history a teljes beszélgetés
     * @return érthetőség, nyelvhelyesség, feladatteljesítés és feedback
     * @throws LanguageModelException ha az értékelés nem állítható elő
     */
    ScenarioEvaluation evaluateScenario(ScenarioConfig config, List<ConversationTurn> history);
}
