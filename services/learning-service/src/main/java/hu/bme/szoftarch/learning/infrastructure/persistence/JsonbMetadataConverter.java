package hu.bme.szoftarch.learning.infrastructure.persistence;

import tools.jackson.core.type.TypeReference;

import jakarta.persistence.Converter;

/**
 * Minimális példa konverter: {@link JsonbMetadata} ↔ JSONB. Egy JPA attribútumon
 * a {@code @Convert(converter = JsonbMetadataConverter.class)} annotációval
 * használható. Az U2 ezt a mintát követi a típusos payload/solution recordokhoz.
 */
@Converter
public class JsonbMetadataConverter extends JsonbConverter<JsonbMetadata> {

    public JsonbMetadataConverter() {
        super(new TypeReference<>() {
        });
    }
}
