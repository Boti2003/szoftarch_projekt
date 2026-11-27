package hu.bme.szoftarch.learning.domain.port;

/**
 * Kimenő domain port az AI-kiértékeléshez az AI service felé (TERV B – U8,
 * KÖTELEZŐ Adapter minta). A domain csak ezt a Spring-mentes interfészt ismeri;
 * a konkrét RestClient adapter az infrastructure/client rétegben él. A port a
 * fordítós feladatok egy-lövetű kiértékelését és a párbeszédes
 * ({@code AI_SCENARIO}) munkamenetek teljes életciklusát fedi le.
 *
 * <p><strong>Biztonság:</strong> a parancsok KIZÁRÓLAG megbízható feladat-
 * konfigurációt és a felhasználói szöveget hordozzák — soha nem tartalmaznak
 * e-mailt, felhasználónevet vagy tokent. A helyes megoldás nem jut vissza a
 * kliensnek; a pont/eredmény szerveroldalon dől el.</p>
 */
public interface AiEvaluationPort {

    /**
     * Kiértékel egy fordítós beküldést az AI service-en.
     *
     * @param command a forrásszöveg, célnyelv, szempontok és a felhasználói fordítás
     * @return a pontszám, az elfogadottság és a megoldást nem szivárogtató visszajelzés
     */
    TranslationEvaluation evaluateTranslation(EvaluateTranslationCommand command);

    /**
     * Elindít egy párbeszédes munkamenetet az AI service-en.
     *
     * @param command a szerep, szituáció, célok, célnyelv és a nyitó üzenet
     * @return az AI-munkamenet azonosítója és a nyitó üzenete
     */
    ScenarioSession startScenarioSession(StartScenarioCommand command);

    /**
     * Elküld egy felhasználói üzenetet a futó párbeszédes munkamenetbe.
     *
     * @param command az AI-munkamenet azonosítója és a felhasználói üzenet
     * @return az AI válasza és a kör sorszáma
     */
    ScenarioMessage sendScenarioMessage(SendMessageCommand command);

    /**
     * Lezár egy párbeszédes munkamenetet és elkéri a végső kiértékelést.
     *
     * @param command az AI-munkamenet azonosítója
     * @return az érthetőség, nyelvtan és feladatteljesítés részpontjai és a visszajelzés
     */
    ScenarioEvaluation completeScenarioSession(CompleteScenarioCommand command);
}
