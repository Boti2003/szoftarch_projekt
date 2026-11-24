package hu.bme.szoftarch.learning.infrastructure.persistence;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import jakarta.persistence.AttributeConverter;

/**
 * Általános JSONB ↔ Java típus konverter alap. A leszármazott konverterek egy
 * konkrét típust (pl. egy record) képeznek le a PostgreSQL {@code JSONB} oszlop
 * szöveges reprezentációjára és vissza. Az {@code exercises.payload} és
 * {@code exercises.solution} típusos recordjait az U2 építi erre.
 *
 * <p>A string alapú (de)szerializáció H2-n (teszt) is működik Testcontainers
 * nélkül; valódi JSONB operátorokhoz U2+ Testcontainerst használ.</p>
 *
 * @param <T> a Java oldali típus
 */
public abstract class JsonbConverter<T> implements AttributeConverter<T, String> {

    private static final ObjectMapper OBJECT_MAPPER = JsonbObjectMapperHolder.instance();

    private final TypeReference<T> typeReference;

    protected JsonbConverter(TypeReference<T> typeReference) {
        this.typeReference = typeReference;
    }

    @Override
    public String convertToDatabaseColumn(T attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to serialize JSONB attribute", e);
        }
    }

    @Override
    public T convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(dbData, typeReference);
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to deserialize JSONB attribute", e);
        }
    }
}
