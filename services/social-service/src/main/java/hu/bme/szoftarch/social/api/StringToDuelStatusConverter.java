package hu.bme.szoftarch.social.api;

import hu.bme.szoftarch.social.domain.duel.DuelStatus;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Case-insensitive {@code String -> DuelStatus} converter for the duel
 * {@code status} query parameter, accepting lower-case contract values. An
 * unknown value throws {@link IllegalArgumentException} (surfaced as a 400).
 */
@Component
public class StringToDuelStatusConverter implements Converter<String, DuelStatus> {
    @Override
    public DuelStatus convert(String source) {
        return DuelStatus.valueOf(source.trim().toUpperCase());
    }
}
