package com.pillapago.evaluation.engine.exception;

/**
 * Excepción lanzada cuando se intenta aplicar un operador a un tipo de dato
 * incompatible o cuando los tipos a comparar no son coherentes entre sí.
 */
public class IncompatibleOperatorException extends RuleEngineException {

    private final String operator;
    private final String actualType;
    private final String targetType;

    public IncompatibleOperatorException(String operator, String actualType, String targetType, String detail) {
        super(String.format("Operador '%s' incompatible entre valor actual (%s) y valor objetivo (%s): %s",
                operator, actualType, targetType, detail));
        this.operator = operator;
        this.actualType = actualType;
        this.targetType = targetType;
    }

    public IncompatibleOperatorException(String message) {
        super(message);
        this.operator = null;
        this.actualType = null;
        this.targetType = null;
    }

    public String getOperator() {
        return operator;
    }

    public String getActualType() {
        return actualType;
    }

    public String getTargetType() {
        return targetType;
    }
}
