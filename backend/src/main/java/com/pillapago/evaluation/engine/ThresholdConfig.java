package com.pillapago.evaluation.engine;

import com.pillapago.evaluation.engine.exception.InvalidThresholdException;

/**
 * Configuración inmutable de umbrales para la toma de decisiones.
 *
 * Restricciones:
 *   0 <= reviewThreshold < rejectThreshold <= 100
 *
 * Lógica de decisión:
 *   score >= rejectThreshold   -> REJECT
 *   score >= reviewThreshold   -> REVIEW
 *   score < reviewThreshold    -> APPROVE
 */
public record ThresholdConfig(
        int reviewThreshold,
        int rejectThreshold
) {
    public ThresholdConfig {
        if (reviewThreshold < 0 || reviewThreshold > 100) {
            throw new InvalidThresholdException("El umbral de revisión debe estar entre 0 y 100 (recibido: " + reviewThreshold + ")");
        }
        if (rejectThreshold < 0 || rejectThreshold > 100) {
            throw new InvalidThresholdException("El umbral de rechazo debe estar entre 0 y 100 (recibido: " + rejectThreshold + ")");
        }
        if (reviewThreshold >= rejectThreshold) {
            throw new InvalidThresholdException(String.format(
                    "El umbral de rechazo (%d) debe ser estrictamente mayor que el umbral de revisión (%d)",
                    rejectThreshold, reviewThreshold));
        }
    }

    /**
     * Umbrales por defecto según especificación Pillapago:
     * Review: 50 puntos, Reject: 80 puntos.
     */
    public static ThresholdConfig defaultConfig() {
        return new ThresholdConfig(50, 80);
    }
}
