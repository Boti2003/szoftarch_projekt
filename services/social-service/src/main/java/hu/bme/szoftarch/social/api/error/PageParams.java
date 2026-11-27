package hu.bme.szoftarch.social.api.error;

import hu.bme.szoftarch.social.application.common.PageQuery;

/**
 * API-layer helper that turns optional {@code page}/{@code size} request
 * parameters into a normalized {@link PageQuery}, centralizing the pagination
 * contract for every list controller (tasks 10.2/10.3).
 *
 * <p>The default-{@value PageQuery#DEFAULT_SIZE} and
 * cap-{@value PageQuery#MAX_SIZE} rule (requirements 8.2/8.3) is enforced once,
 * inside {@link PageQuery} itself; this helper exists so controllers express
 * the intent in a single readable call
 * ({@code PageParams.toQuery(page, size)}) and so the resolution point is
 * discoverable from the API package. A negative page clamps to 0, a
 * non-positive size falls back to the default, and an over-cap size is clamped
 * to the maximum.
 *
 * <p>Controllers should declare the raw parameters as
 * {@code @RequestParam(required = false) Integer page} and
 * {@code @RequestParam(required = false) Integer size}, then call
 * {@link #toQuery(Integer, Integer)} and pass the result to the use case.
 */
public final class PageParams {

    private PageParams() {
    }

    /**
     * Normalizes raw, possibly-{@code null} request parameters into a bounded
     * {@link PageQuery} (default size {@value PageQuery#DEFAULT_SIZE}, capped at
     * {@value PageQuery#MAX_SIZE}).
     *
     * @param page the requested zero-based page index, or {@code null} for 0
     * @param size the requested page size, or {@code null} for the default
     * @return a normalized query safe to hand to a use case
     */
    public static PageQuery toQuery(Integer page, Integer size) {
        return PageQuery.of(page, size);
    }
}
