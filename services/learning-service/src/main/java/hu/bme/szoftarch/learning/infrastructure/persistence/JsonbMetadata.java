package hu.bme.szoftarch.learning.infrastructure.persistence;

import java.util.Map;

/**
 * Minimális példa JSONB record a konverter-mechanizmus demonstrálására
 * (U0). A konkrét, típusonkénti exercise payload/solution recordok az U2-ben
 * készülnek; ez csak azt mutatja, hogy egy record oda-vissza konvertálható.
 *
 * @param schemaVersion a JSONB tartalom séma-verziója
 * @param attributes    tetszőleges kulcs-érték metaadatok
 */
public record JsonbMetadata(int schemaVersion, Map<String, Object> attributes) {
}
