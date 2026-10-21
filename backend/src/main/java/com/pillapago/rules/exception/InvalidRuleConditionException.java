package com.pillapago.rules.exception;

/**
 * Excepción lanzada cuando una condición de una regla viola las restricciones
 * del lenguaje acotado del motor (campo desconocido, operador incompatible, valor inválido).
 */
public class InvalidRuleConditionException extends RuntimeException {

    public InvalidRuleConditionException(String message) {
        super(message);
    }

    public InvalidRuleConditionException(String message, Throwable cause) {
        super(message, cause);
    }
}
