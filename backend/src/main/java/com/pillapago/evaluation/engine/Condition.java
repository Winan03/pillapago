package com.pillapago.evaluation.engine;

import com.pillapago.evaluation.engine.exception.InvalidConditionException;

import java.util.Objects;

/**
 * Representa una condición individual de una regla antifraude.
 * Estructura: campo + operador + valor objetivo.
 *
 * Ejemplo:
 *   campo: "amount"
 *   operador: GREATER_THAN (>)
 *   targetValue: 1000.00
 */
public record Condition(
        String field,
        Operator operator,
        Object targetValue
) {
    public Condition {
        if (field == null || field.trim().isEmpty()) {
            throw new InvalidConditionException("El nombre del campo de la condición no puede ser nulo ni vacío");
        }
        if (operator == null) {
            throw new InvalidConditionException("El operador de la condición no puede ser nulo");
        }
        if (targetValue == null) {
            throw new InvalidConditionException("El valor objetivo de la condición no puede ser nulo");
        }
        field = field.trim();
    }

    /**
     * Constructor de conveniencia que resuelve el operador a partir de su símbolo de texto.
     */
    public Condition(String field, String operatorSymbol, Object targetValue) {
        this(field, Operator.fromSymbol(operatorSymbol), targetValue);
    }
}
