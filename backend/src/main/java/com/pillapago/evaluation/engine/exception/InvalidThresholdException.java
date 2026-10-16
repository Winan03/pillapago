package com.pillapago.evaluation.engine.exception;

/**
 * Excepción lanzada cuando los umbrales de decisión no cumplen las restricciones de negocio:
 * 0 <= reviewThreshold < rejectThreshold <= 100.
 */
public class InvalidThresholdException extends RuleEngineException {

    public InvalidThresholdException(String message) {
        super(message);
    }
}
