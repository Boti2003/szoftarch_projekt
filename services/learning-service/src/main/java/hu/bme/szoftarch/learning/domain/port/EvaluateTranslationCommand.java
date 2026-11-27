package hu.bme.szoftarch.learning.domain.port;

import java.util.List;

/**
 * A fordítós AI-kiértékelés kimenő parancsa (TERV B – U8, Adapter minta
 * bemenete). Spring- és HTTP-mentes domain érték; az infrastruktúra adaptere
 * fordítja REST-hívásra. KIZÁRÓLAG megbízható feladat-konfigurációt és a
 * felhasználói szöveget hordozza (nincs e-mail/felhasználónév/token).
 *
 * @param sourceText         a lefordítandó forrásszöveg
 * @param targetLanguageCode a célnyelv kódja (pl. {@code en})
 * @param rubric             az AI-értékelés szempontjai
 * @param userTranslation    a felhasználó fordítása
 */
public record EvaluateTranslationCommand(String sourceText, String targetLanguageCode,
        List<String> rubric, String userTranslation) {
}
