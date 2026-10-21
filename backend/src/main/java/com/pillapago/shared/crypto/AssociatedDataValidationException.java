package com.pillapago.shared.crypto;

/**
 * Excepción lanzada cuando la autenticación de datos asociados (AEAD AAD)
 * falla en tiempo de descifrado, indicando que el texto cifrado ha sido copiado,
 * manipulado o transplantado a otra fila, columna o comercio.
 */
public class AssociatedDataValidationException extends CryptoOperationException {

    public AssociatedDataValidationException(String message) {
        super(message);
    }

    public AssociatedDataValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
