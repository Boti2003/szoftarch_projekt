package hu.bme.szoftarch.learning.domain.model;

/**
 * A pontjóváírás életciklusa az Identity adapteren keresztül (U5).
 */
public enum PointAwardStatus {
    NOT_APPLICABLE,
    PENDING,
    AWARDED,
    FAILED_RETRYABLE
}
