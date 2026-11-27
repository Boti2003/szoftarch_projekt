package hu.bme.szoftarch.social.application.common;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * An application-layer paginated result carrying a page of items together with
 * the pagination metadata the API envelope needs.
 *
 * <p>This mirrors the {@code Page_Response} contract from the requirements
 * (fields {@code items, page, size, totalElements, totalPages}). It is defined
 * here, in the application layer, so the use-case services stay free of any
 * Spring Data type; the API layer (task 10.1) wraps it in its public
 * {@code PageResponse} envelope <strong>using these exact field names and
 * semantics</strong>:
 *
 * <ul>
 *   <li>{@code items} — the items on this page, in a stable order.</li>
 *   <li>{@code page} — the zero-based page index.</li>
 *   <li>{@code size} — the applied page size.</li>
 *   <li>{@code totalElements} — total number of matching elements across all pages.</li>
 *   <li>{@code totalPages} — total number of pages for the applied size.</li>
 * </ul>
 *
 * @param <T>           the item type
 * @param items         the page contents; copied defensively and never {@code null}
 * @param page          the zero-based page index
 * @param size          the applied page size
 * @param totalElements total matching elements across all pages
 * @param totalPages    total number of pages
 */
public record PageResult<T>(List<T> items, int page, int size, long totalElements, int totalPages) {

    public PageResult {
        items = List.copyOf(Objects.requireNonNull(items, "items must not be null"));
    }

    /**
     * Builds a {@link PageResult} from an explicit item list and the total
     * element count, deriving {@code totalPages} from {@code size}.
     *
     * @param items         the page contents
     * @param page          the zero-based page index
     * @param size          the applied page size (must be positive)
     * @param totalElements total matching elements across all pages
     * @param <T>           the item type
     * @return the assembled result
     */
    public static <T> PageResult<T> of(List<T> items, int page, int size, long totalElements) {
        int totalPages = size <= 0 ? 0 : (int) ((totalElements + size - 1) / size);
        return new PageResult<>(items, page, size, totalElements, totalPages);
    }

    /**
     * Returns a new result with each item mapped by {@code mapper}, preserving
     * all pagination metadata. Useful for converting entity pages into view
     * pages without recomputing totals.
     *
     * @param mapper the per-item transform
     * @param <R>    the mapped item type
     * @return a result over the mapped items
     */
    public <R> PageResult<R> map(Function<? super T, ? extends R> mapper) {
        Objects.requireNonNull(mapper, "mapper must not be null");
        return new PageResult<>(items.stream().map(mapper).map(r -> (R) r).toList(),
                page, size, totalElements, totalPages);
    }
}
