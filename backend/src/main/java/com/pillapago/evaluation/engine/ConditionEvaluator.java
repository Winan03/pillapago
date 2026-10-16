package com.pillapago.evaluation.engine;

import com.pillapago.evaluation.engine.exception.InvalidConditionException;

import java.util.Objects;

/**
 * Servicio de evaluación de condiciones individuales contra el EvaluationContext.
 * Diseñado para ser Java puro, thread-safe, libre de estado y de alta velocidad.
 */
public class ConditionEvaluator {

    /**
     * Evalúa una condición contra el contexto de evaluación proporcionado.
     *
     * @param condition Condición a evaluar (campo + operador + valor)
     * @param context Contexto con los atributos de la transacción y métricas de velocidad
     * @return true si la condición se cumple; false en caso contrario
     * @throws com.pillapago.evaluation.engine.exception.UnknownFieldException si el campo no existe en el contexto
     * @throws com.pillapago.evaluation.engine.exception.IncompatibleOperatorException si el operador no es compatible con el tipo
     */
    public boolean evaluate(Condition condition, EvaluationContext context) {
        Objects.requireNonNull(condition, "La condición no puede ser nula");
        Objects.requireNonNull(context, "El contexto de evaluación no puede ser nulo");

        Object actualValue = context.get(condition.field());
        return condition.operator().apply(actualValue, condition.targetValue());
    }
}
