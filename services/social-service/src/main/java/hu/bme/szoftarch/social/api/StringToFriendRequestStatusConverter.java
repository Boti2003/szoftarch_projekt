package hu.bme.szoftarch.social.api;

import hu.bme.szoftarch.social.domain.friend.FriendRequestStatus;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Case-insensitive {@code String -> FriendRequestStatus} converter for the
 * {@code status} query parameter, accepting lower-case contract values. An
 * unknown value throws {@link IllegalArgumentException} (surfaced as a 400).
 */
@Component
public class StringToFriendRequestStatusConverter implements Converter<String, FriendRequestStatus> {
    @Override
    public FriendRequestStatus convert(String source) {
        return FriendRequestStatus.valueOf(source.trim().toUpperCase());
    }
}
