package hu.bme.szoftarch.learning;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import hu.bme.szoftarch.learning.infrastructure.persistence.JsonbMetadata;
import hu.bme.szoftarch.learning.infrastructure.persistence.JsonbMetadataConverter;

import org.junit.jupiter.api.Test;

class JsonbConverterTest {

    private final JsonbMetadataConverter converter = new JsonbMetadataConverter();

    @Test
    void roundTripsRecordThroughJsonString() {
        JsonbMetadata metadata = new JsonbMetadata(1, Map.of("locale", "en", "retries", 3));

        String json = converter.convertToDatabaseColumn(metadata);
        JsonbMetadata restored = converter.convertToEntityAttribute(json);

        assertThat(json).contains("\"schemaVersion\":1");
        assertThat(restored).isEqualTo(metadata);
    }

    @Test
    void handlesNulls() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
        assertThat(converter.convertToEntityAttribute("")).isNull();
    }
}
