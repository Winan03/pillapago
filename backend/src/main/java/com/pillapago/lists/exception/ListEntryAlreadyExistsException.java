package com.pillapago.lists.exception;

/**
 * Excepción lanzada cuando se intenta agregar un identificador que ya existe en la misma lista del comercio.
 * Se traduce en HTTP 409 Conflict (RFC 7807).
 */
public class ListEntryAlreadyExistsException extends RuntimeException {

    public ListEntryAlreadyExistsException(String message) {
        super(message);
    }
}
