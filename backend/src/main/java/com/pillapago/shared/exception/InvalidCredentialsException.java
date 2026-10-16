package com.pillapago.shared.exception;

/**
 * Excepción lanzada cuando las credenciales no son válidas o el usuario no existe.
 * Mensaje intencionalmente genérico para evitar enumeración de usuarios.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Credenciales inválidas");
    }

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
