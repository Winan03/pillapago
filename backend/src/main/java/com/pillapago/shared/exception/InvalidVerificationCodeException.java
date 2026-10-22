package com.pillapago.shared.exception;

/**
 * Excepción lanzada cuando el código de verificación es erróneo o ha expirado (400 Bad Request).
 */
public class InvalidVerificationCodeException extends RuntimeException {

    public InvalidVerificationCodeException(String message) {
        super(message);
    }
}
