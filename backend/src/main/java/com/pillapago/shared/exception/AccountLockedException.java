package com.pillapago.shared.exception;

/**
 * Excepción lanzada cuando una cuenta o código de verificación queda bloqueado por exceder intentos (423 Locked).
 */
public class AccountLockedException extends RuntimeException {

    public AccountLockedException(String message) {
        super(message);
    }
}
