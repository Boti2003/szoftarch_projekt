package hu.bme.szoftarch.social.api.error;

import hu.bme.szoftarch.social.application.common.PageResult;

import java.util.List;
import java.util.Objects;

/**
 * The public pagination envelope returned by every list endpoint, serialized to
 * JSON with the field names mandated by the {@code Page_Response} contract
 * (requirements 8.1): {@code items}, {@code page}, {@code size},
 * {@code totalElements}, {@code totalPages}.
 *
 * <p>It is the API-layer mirror of the application-layer
 * {@link PageResult}: same field names, same semantics (zero-based
 * {@code page}, applied {@code size}, totals across all pages). Controllers
 * (tasks 10.2/10.3) build it from a {@link PageResult} via {@link #from} after
 * mapping items to their API DTOs, or directly via {@link #of} when they hold
 * the pieces already.
 *
 * <p><strong>Location choice.</strong> This envelope lives in
 * {@code api.error} alongside {@code GlobalExceptionHandler} because the design
 * ("Code structure") groups the shared API concerns — problem+json and the
 * pagination envelope — in a single {@code error} package. It carries no error
 * semantics itself; the package simply hosts the shared API-layer response
 * types.
 *
 * @param <T>           the API DTO item type
 * @param items         the items on this page, in a stable order; never {@code null}
 * @param page          the zero-based page index
 * @param size          the applied page size
 * @param totalElements total matching elements across all pages
 * @param totalPages    total number of pages for the applied size
 */
public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {

    public PageResponse {
        items = List.copyOf(Objects.requireNonNull(items, "items must not be null"));
    }

    /**
     * Wraps an application-layer {@link PageResult} in the public envelope,
     * copying every pagination field verbatim.
     *
     * @param result the application result to mirror; must not be {@code null}
     * @param <T>     the item type
     * @return an envelope with the same items and pagination metadata
     */
    public static <T> PageResponse<T> from(PageResult<T> result) {
        Objects.requireNonNull(result, "result must not be null");
        return new PageResponse<>(
                result.items(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages());
    }

    /**
     * Builds an envelope from explicit parts, used when a controller already
     * holds the mapped DTO page and its totals.
     *
     * @param items         the page contents
     * @param page          the zero-based page index
     * @param size          the applied page size
     * @param totalElements total matching elements across all pages
     * @param totalPages    total number of pages
     * @param <T>           the item type
     * @return the assembled envelope
     */
    public static <T> PageResponse<T> of(List<T> items, int page, int size, long totalElements, int totalPages) {
        return new PageResponse<>(items, page, size, totalElements, totalPages);
    }
}
