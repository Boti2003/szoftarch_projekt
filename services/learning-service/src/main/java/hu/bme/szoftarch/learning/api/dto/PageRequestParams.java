package hu.bme.szoftarch.learning.api.dto;

/**
 * Közös lapozási paraméterek. Alap lapméret 20, maximum 100 (TERV 7.
 * API-konvenciók). A controllerek ezt használják a query paraméterek
 * normalizálására.
 */
public record PageRequestParams(int page, int size) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public PageRequestParams {
        if (page < 0) {
            page = 0;
        }
        if (size <= 0) {
            size = DEFAULT_SIZE;
        } else if (size > MAX_SIZE) {
            size = MAX_SIZE;
        }
    }

    /**
     * Null-biztonságos gyár a query paraméterekhez: hiányzó értékekre az
     * alapértelmezéseket alkalmazza.
     */
    public static PageRequestParams of(Integer page, Integer size) {
        return new PageRequestParams(
                page == null ? 0 : page,
                size == null ? DEFAULT_SIZE : size);
    }
}
