package com.pillapago.shared.exception;

/**
 * Excepción lanzada cuando se intenta registrar un correo ya existente en el sistema (409 Conflict).
 */
public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException(String message) {
        super(message);
    }
}
