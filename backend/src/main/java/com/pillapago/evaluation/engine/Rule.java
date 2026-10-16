package com.pillapago.evaluation.engine;

import com.pillapago.evaluation.engine.exception.InvalidRuleException;

import java.util.List;

/**
 * Representa una regla de riesgo antifraude.
 * Contiene una lista de condiciones (evaluadas conjuntamente con lógica AND),
 * un puntaje de riesgo (0 a 100) y su estado de activación.
 */
public record Rule(
        String id,
        String name,
        String description,
        List<Condition> conditions,
        int points,
        boolean active
) {
    public Rule {
        if (id == null || id.trim().isEmpty()) {
            throw new InvalidRuleException("El ID de la regla no puede ser nulo ni vacío");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new InvalidRuleException("El nombre de la regla no puede ser nulo ni vacío");
        }
        if (conditions == null || conditions.isEmpty()) {
            throw new InvalidRuleException("La regla '" + name + "' debe contener al menos una condición");
        }
        if (points < 0 || points > 100) {
            throw new InvalidRuleException("Los puntos de riesgo de la regla '" + name + "' deben estar entre 0 y 100 (recibido: " + points + ")");
        }
        id = id.trim();
        name = name.trim();
        description = description != null ? description.trim() : "";
        conditions = List.copyOf(conditions);
    }

    /**
     * Constructor para regla activa con lista de condiciones.
     */
    public Rule(String id, String name, String description, List<Condition> conditions, int points) {
        this(id, name, description, conditions, points, true);
    }

    /**
     * Constructor de conveniencia para una regla con una única condición.
     */
    public Rule(String id, String name, String description, Condition condition, int points) {
        this(id, name, description, List.of(condition), points, true);
    }

    /**
     * Constructor de conveniencia para una regla con una única condición y estado activo configurable.
     */
    public Rule(String id, String name, String description, Condition condition, int points, boolean active) {
        this(id, name, description, List.of(condition), points, active);
    }
}
