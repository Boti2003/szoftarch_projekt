package hu.bme.szoftarch.learning.infrastructure.client;

/**
 * Az Identity pontjóváírás válaszának LOKÁLIS DTO-ja (TERV D20 / 3.4: nincs
 * közös domain-JAR, csak a ténylegesen használt mezőket deszerializáljuk). A
 * learning nem tárolja le az Identity-adatot; a kimenetet a HTTP-státusz
 * vezérli, ez a body csak diagnosztikai célú és opcionális.
 *
 * @param status az Identity által jelzett állapot (pl. {@code "AWARDED"})
 */
public record IdentityPointsResponse(String status) {
}
