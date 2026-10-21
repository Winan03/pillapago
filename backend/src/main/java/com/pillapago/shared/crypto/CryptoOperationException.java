package com.pillapago.shared.crypto;

/**
 * Excepción lanzada cuando ocurre un error operacional o criptográfico
 * durante el cifrado, descifrado o gestión de claves.
 */
public class CryptoOperationException extends RuntimeException {

    public CryptoOperationException(String message) {
        super(message);
    }

    public CryptoOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
