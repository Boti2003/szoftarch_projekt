package hu.bme.szoftarch.social.application.common;

/**
 * A normalized pagination request carried from the API layer into the
 * application use cases.
 *
 * <p>The zero-based {@code page} index and the applied {@code size} follow the
 * pagination contract of the spec (requirements 8.2/8.3): the default size is
 * {@value #DEFAULT_SIZE} and the maximum applied size is capped at
 * {@value #MAX_SIZE}. Normalization is performed here so every use case sees a
 * valid, bounded query regardless of the raw request parameters.
 *
 * <p>Pure application-layer value with no Spring or persistence dependency. The
 * infrastructure/repository layer translates this into a Spring Data
 * {@code Pageable}; the API layer builds it from request parameters.
 *
 * @param page the zero-based page index (negative values are clamped to 0)
 * @param size the applied page size (clamped to {@code [1, 100]},
 *             defaulting to {@value #DEFAULT_SIZE} when non-positive)
 */
public record PageQuery(int page, int size) {

    /** Default page size applied when the caller omits a size (requirement 8.2). */
    public static final int DEFAULT_SIZE = 20;

    /** Maximum page size the service will ever apply (requirement 8.3). */
    public static final int MAX_SIZE = 100;

    /**
     * Normalizes the page index and size on construction: a negative page
     * becomes 0, a non-positive size becomes {@value #DEFAULT_SIZE}, and a size
     * above {@value #MAX_SIZE} is capped at {@value #MAX_SIZE}.
     */
    public PageQuery {
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
     * Builds a normalized query from raw, possibly-null request parameters.
     *
     * @param page the requested zero-based page index, or {@code null} for 0
     * @param size the requested page size, or {@code null} for the default
     * @return a normalized {@link PageQuery}
     */
    public static PageQuery of(Integer page, Integer size) {
        return new PageQuery(page == null ? 0 : page, size == null ? DEFAULT_SIZE : size);
    }

    /** @return a normalized query for the first page with the default size. */
    public static PageQuery firstPage() {
        return new PageQuery(0, DEFAULT_SIZE);
    }
}
