package com.pillapago.evaluation.engine;

import com.pillapago.evaluation.engine.exception.UnknownFieldException;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Contexto de evaluación inmutable que contiene los datos de la transacción
 * y las métricas de velocidad calculadas.
 * Proporciona acceso O(1) seguro y tipado por nombre de campo sin usar reflection.
 */
public record EvaluationContext(
        Map<String, Object> attributes
) {
    public EvaluationContext {
        if (attributes == null) {
            attributes = Collections.emptyMap();
        } else {
            // Guardamos una copia inmutable del mapa
            attributes = Map.copyOf(attributes);
        }
    }

    /**
     * Crea un contexto a partir de un mapa de atributos.
     */
    public static EvaluationContext from(Map<String, Object> map) {
        return new EvaluationContext(map);
    }

    /**
     * Crea un contexto vacío.
     */
    public static EvaluationContext empty() {
        return new EvaluationContext(Collections.emptyMap());
    }

    /**
     * Verifica si un campo existe en el contexto.
     * Busca por coincidencia exacta o ignorando mayúsculas/minúsculas.
     */
    public boolean has(String field) {
        if (field == null) {
            return false;
        }
        if (attributes.containsKey(field)) {
            return true;
        }
        for (String key : attributes.keySet()) {
            if (key.equalsIgnoreCase(field)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Obtiene el valor del campo especificado.
     *
     * @param field Nombre del campo (ej. "amount", "cardCountry", "velocity.device_tx_10m")
     * @return El valor asociado al campo
     * @throws UnknownFieldException si el campo no existe en el contexto
     */
    public Object get(String field) {
        if (field == null) {
            throw new UnknownFieldException("null", attributes.keySet());
        }
        if (attributes.containsKey(field)) {
            return attributes.get(field);
        }
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(field)) {
                return entry.getValue();
            }
        }
        throw new UnknownFieldException(field, attributes.keySet());
    }

    /**
     * Retorna el conjunto de nombres de campos disponibles en el contexto.
     */
    public Set<String> getAvailableFields() {
        return attributes.keySet();
    }

    /**
     * Builder para construir EvaluationContext de manera fluida.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final Map<String, Object> map = new HashMap<>();

        public Builder put(String key, Object value) {
            if (key != null) {
                map.put(key, value);
            }
            return this;
        }

        public Builder putAll(Map<String, Object> entries) {
            if (entries != null) {
                map.putAll(entries);
            }
            return this;
        }

        public EvaluationContext build() {
            return new EvaluationContext(map);
        }
    }
}
