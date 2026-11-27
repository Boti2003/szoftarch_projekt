package hu.bme.szoftarch.social.api;

import hu.bme.szoftarch.social.application.friend.Direction;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Case-insensitive {@code String -> Direction} converter for the
 * {@code direction} query parameter. The OpenAPI contract advertises lower-case
 * values ({@code incoming}/{@code outgoing}); this maps them (trimmed, any case)
 * onto the upper-case enum. Registered automatically as a Spring
 * {@link Converter} bean, so it takes precedence over the default enum binding.
 * An unknown value throws {@link IllegalArgumentException}, which the API layer
 * surfaces as a 400.
 */
@Component
public class StringToDirectionConverter implements Converter<String, Direction> {
    @Override
    public Direction convert(String source) {
        return Direction.valueOf(source.trim().toUpperCase());
    }
}
