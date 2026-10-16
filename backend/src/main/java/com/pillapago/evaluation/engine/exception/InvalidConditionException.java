package com.pillapago.evaluation.engine.exception;

/**
 * Excepción lanzada cuando una condición no cumple los criterios mínimos de validación
 * (ej. campo en blanco, operador nulo, etc.).
 */
public class InvalidConditionException extends RuleEngineException {

    public InvalidConditionException(String message) {
        super(message);
    }
}
