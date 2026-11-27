package hu.bme.szoftarch.social.application.friend;

/**
 * The direction of a friend-request listing relative to the authenticated user.
 *
 * <ul>
 *   <li>{@link #INCOMING} — requests where the user is the <em>receiver</em>.</li>
 *   <li>{@link #OUTGOING} — requests where the user is the <em>sender</em>.</li>
 * </ul>
 *
 * <p>Pure application-layer value with no Spring or persistence dependency;
 * the API layer (task 10.2) maps its request parameter onto this enum.
 */
public enum Direction {
    INCOMING,
    OUTGOING
}
