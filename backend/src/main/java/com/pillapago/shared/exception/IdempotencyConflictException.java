package com.pillapago.shared.exception;

/**
 * Excepción lanzada cuando se recibe una solicitud con un transactionId ya utilizado
 * pero cuyos datos (payload hash) difieren de la transacción original (HTTP 409 Conflict).
 */
public class IdempotencyConflictException extends RuntimeException {

    private final String transactionId;
    private final String userMessage;

    public IdempotencyConflictException(String transactionId, String message, String userMessage) {
        super(message);
        this.transactionId = transactionId;
        this.userMessage = userMessage;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getUserMessage() {
        return userMessage;
    }
}
