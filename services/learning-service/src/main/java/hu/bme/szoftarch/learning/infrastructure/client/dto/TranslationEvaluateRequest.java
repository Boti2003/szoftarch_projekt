package hu.bme.szoftarch.learning.infrastructure.client.dto;

import java.util.List;

/**
 * Fordítós kiértékelés kérés-body-ja az AI belső API-nak
 * ({@code POST /internal/v1/translations:evaluate}, TERV 7.7). Lokális DTO: csak
 * a ténylegesen használt, megbízható feladat-konfiguráció és a felhasználói
 * szöveg (nincs e-mail/felhasználónév/token).
 *
 * @param sourceText         a lefordítandó forrásszöveg
 * @param targetLanguageCode a célnyelv kódja
 * @param rubric             az AI-értékelés szempontjai
 * @param userTranslation    a felhasználó fordítása
 */
public record TranslationEvaluateRequest(String sourceText, String targetLanguageCode,
        List<String> rubric, String userTranslation) {
}
