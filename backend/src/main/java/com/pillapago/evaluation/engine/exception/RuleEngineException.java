package com.pillapago.evaluation.engine.exception;

/**
 * Excepción base para todos los errores originados en el motor de decisiones de reglas.
 * Es una RuntimeException pura sin dependencias de frameworks externos.
 */
public class RuleEngineException extends RuntimeException {

    public RuleEngineException(String message) {
        super(message);
    }

    public RuleEngineException(String message, Throwable cause) {
        super(message, cause);
    }
}
