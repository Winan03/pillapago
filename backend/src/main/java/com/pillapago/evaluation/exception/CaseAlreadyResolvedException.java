package com.pillapago.evaluation.exception;

/**
 * Excepción lanzada cuando se intenta reclamar o resolver un caso que ya ha sido resuelto (inmutabilidad).
 */
public class CaseAlreadyResolvedException extends RuntimeException {

    public CaseAlreadyResolvedException(String message) {
        super(message);
    }
}
