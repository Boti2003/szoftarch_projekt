package hu.bme.szoftarch.learning.api.dto;

import java.util.List;

/**
 * Közös lapozott válasz (TERV 7. API-konvenciók):
 * {@code items, page, size, totalElements, totalPages}.
 */
public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static <T> PageResponse<T> of(List<T> items, int page, int size, long totalElements) {
        int totalPages = size <= 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new PageResponse<>(items, page, size, totalElements, totalPages);
    }
}
