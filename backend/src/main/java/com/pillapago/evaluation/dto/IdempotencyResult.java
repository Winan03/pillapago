package com.pillapago.evaluation.dto;

import java.util.Optional;

/**
 * Resultado de la verificación de idempotencia para una transacción.
 */
public record IdempotencyResult(
        Status status,
        String transactionId,
        String cachedResponseJson
) {
    public enum Status {
        ACQUIRED,
        COMPLETED,
        CONFLICT,
        IN_PROGRESS
    }

    public static IdempotencyResult acquired(String transactionId) {
        return new IdempotencyResult(Status.ACQUIRED, transactionId, null);
    }

    public static IdempotencyResult completed(String transactionId, String cachedResponseJson) {
        return new IdempotencyResult(Status.COMPLETED, transactionId, cachedResponseJson);
    }

    public static IdempotencyResult conflict(String transactionId) {
        return new IdempotencyResult(Status.CONFLICT, transactionId, null);
    }

    public static IdempotencyResult inProgress(String transactionId) {
        return new IdempotencyResult(Status.IN_PROGRESS, transactionId, null);
    }

    public boolean isAcquired() {
        return status == Status.ACQUIRED;
    }

    public boolean isCompleted() {
        return status == Status.COMPLETED;
    }

    public boolean isConflict() {
        return status == Status.CONFLICT;
    }

    public Optional<String> getCachedResponse() {
        return Optional.ofNullable(cachedResponseJson);
    }
}
