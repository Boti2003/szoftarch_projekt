package hu.bme.szoftarch.ai.domain;

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
}
