package hu.bme.szoftarch.learning.infrastructure.persistence;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;

import jakarta.persistence.Converter;

/**
 * Típus-agnosztikus JSONB ↔ {@link JsonNode} konverter. Az {@code exercises}
 * {@code payload}/{@code solution} oszlopait nyers JSON-faként tárolja, mert egy
 * sor típusa futásidőben derül ki; a típusos recordra deszerializálást az
 * {@link ExerciseContentCodec} végzi a feladattípus alapján.
 */
@Converter
public class JsonbNodeConverter extends JsonbConverter<JsonNode> {

    public JsonbNodeConverter() {
        super(new TypeReference<>() {
        });
    }
}
