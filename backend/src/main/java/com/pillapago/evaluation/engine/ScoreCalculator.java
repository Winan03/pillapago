package com.pillapago.evaluation.engine;

import java.util.List;
import java.util.Objects;

/**
 * Calculador de puntaje de riesgo y resolución de decisión antifraude.
 * Implementa la regla de suma con tope estricto en 100 y mapeo contra ThresholdConfig.
 */
public class ScoreCalculator {

    public static final int MAX_SCORE = 100;
    public static final int MIN_SCORE = 0;

    /**
     * Suma los puntos de las reglas activadas aplicando un tope máximo estricto en 100.
     * Si la lista de motivos está vacía, el score es 0.
     *
     * @param reasons Lista de motivos de las reglas que se activaron
     * @return Score de riesgo acotado entre 0 y 100
     */
    public int calculateScore(List<RuleReason> reasons) {
        if (reasons == null || reasons.isEmpty()) {
            return MIN_SCORE;
        }

        int total = 0;
        for (RuleReason reason : reasons) {
            total += reason.points();
        }

        return Math.min(MAX_SCORE, total);
    }

    /**
     * Resuelve la decisión antifraude en función del score y la configuración de umbrales.
     *
     * @param score Puntaje de riesgo (0 - 100)
     * @param thresholds Configuración de umbrales (review y reject)
     * @return Decision correspondiente: APPROVE, REVIEW o REJECT
     */
    public Decision resolveDecision(int score, ThresholdConfig thresholds) {
        Objects.requireNonNull(thresholds, "La configuración de umbrales no puede ser nula");

        if (score >= thresholds.rejectThreshold()) {
            return Decision.REJECT;
        }
        if (score >= thresholds.reviewThreshold()) {
            return Decision.REVIEW;
        }
        return Decision.APPROVE;
    }
}
