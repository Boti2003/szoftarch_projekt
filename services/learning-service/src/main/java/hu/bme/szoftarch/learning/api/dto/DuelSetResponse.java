package hu.bme.szoftarch.learning.api.dto;

import java.util.List;
import java.util.UUID;

/**
 * Párbaj-feladatkészlet válasz a belső {@code POST /internal/v1/duel-sets} és
 * {@code GET /internal/v1/duel-sets/{setId}} végpontokhoz (TERV 5.5, 7.5). A
 * készlet azonosítóját és a feladatprezentációkat (MEGOLDÁS NÉLKÜL, pozíció
 * sorrendben) adja, hogy mindkét résztvevő pontosan ugyanazt az 5 feladatot
 * kapja ugyanabban a sorrendben.
 *
 * @param setId     a létrehozott/meglévő duel_set azonosítója
 * @param exercises a feladatprezentációk pozíció sorrendben, solution nélkül
 */
public record DuelSetResponse(
        UUID setId,
        List<ExercisePresentationResponse> exercises) {
}
