package com.pillapago.shared.exception;

/**
 * Excepción lanzada cuando la confirmación de contraseña no coincide (400 Bad Request).
 */
public class PasswordMismatchException extends RuntimeException {

    public PasswordMismatchException(String message) {
        super(message);
    }
}
