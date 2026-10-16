package com.pillapago.evaluation.engine.exception;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * Excepción lanzada cuando una condición hace referencia a un campo
 * que no existe en el EvaluationContext.
 */
public class UnknownFieldException extends RuleEngineException {

    private final String fieldName;
    private final Set<String> availableFields;

    public UnknownFieldException(String fieldName, Collection<String> availableFields) {
        super(String.format("El campo '%s' no existe en el contexto de evaluación. Campos disponibles: %s",
                fieldName, availableFields != null ? availableFields : Collections.emptySet()));
        this.fieldName = fieldName;
        this.availableFields = availableFields != null ? Set.copyOf(availableFields) : Collections.emptySet();
    }

    public String getFieldName() {
        return fieldName;
    }

    public Set<String> getAvailableFields() {
        return availableFields;
    }
}
