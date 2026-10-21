package com.pillapago.shared.exception;

/**
 * Excepción lanzada cuando el servicio de Redis no está disponible,
 * se encuentra caído o la operación excede el timeout configurado.
 * Permite a las capas superiores aplicar la política fail-open / fail-closed.
 */
public class RedisUnavailableException extends RuntimeException {

    private final String operation;

    public RedisUnavailableException(String operation, String message) {
        super(message);
        this.operation = operation;
    }

    public RedisUnavailableException(String operation, String message, Throwable cause) {
        super(message, cause);
        this.operation = operation;
    }

    public String getOperation() {
        return operation;
    }
}
