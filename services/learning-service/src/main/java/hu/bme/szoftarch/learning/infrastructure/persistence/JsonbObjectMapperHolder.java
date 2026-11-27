package hu.bme.szoftarch.learning.infrastructure.persistence;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * A JSONB (de)szerializációhoz használt, stabil {@link ObjectMapper}. A JPA
 * converterek statikusak, ezért a Spring {@code ObjectMapper} bean helyett egy
 * önálló, rekord-barát példányt használunk.
 */
final class JsonbObjectMapperHolder {

    private static final ObjectMapper INSTANCE = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();

    private JsonbObjectMapperHolder() {
    }

    static ObjectMapper instance() {
        return INSTANCE;
    }
}
