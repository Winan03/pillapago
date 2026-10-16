package com.pillapago.evaluation.engine;

/**
 * Representa el motivo por el cual una regla sumó puntos de riesgo a la decisión final.
 *
 * Ejemplo en respuesta JSON de Pillapago:
 *   {"rule": "Monto inusual", "points": 30}
 */
public record RuleReason(
        String ruleId,
        String ruleName,
        int points
) {
    public RuleReason {
        if (ruleId == null || ruleId.trim().isEmpty()) {
            throw new IllegalArgumentException("El ruleId del motivo no puede ser nulo ni vacío");
        }
        if (ruleName == null || ruleName.trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre de la regla del motivo no puede ser nulo ni vacío");
        }
        if (points < 0 || points > 100) {
            throw new IllegalArgumentException("Los puntos de riesgo del motivo deben estar entre 0 y 100");
        }
        ruleId = ruleId.trim();
        ruleName = ruleName.trim();
    }
}
