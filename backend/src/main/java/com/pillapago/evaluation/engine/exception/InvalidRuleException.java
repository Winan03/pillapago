package com.pillapago.evaluation.engine.exception;

/**
 * Excepción lanzada cuando una regla está mal definida
 * (ej. ID nulo/vacío, nombre vacío, puntaje menor a 0 o mayor a 100).
 */
public class InvalidRuleException extends RuleEngineException {

    public InvalidRuleException(String message) {
        super(message);
    }
}
