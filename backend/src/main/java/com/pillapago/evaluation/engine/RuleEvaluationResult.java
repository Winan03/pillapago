package com.pillapago.evaluation.engine;

import java.util.Collections;
import java.util.List;

/**
 * Resultado inmutable emitido por el motor de evaluación de reglas.
 */
public record RuleEvaluationResult(
        Decision decision,
        int riskScore,
        List<RuleReason> reasons,
        int evaluatedRulesCount,
        int triggeredRulesCount
) {
    public RuleEvaluationResult {
        if (decision == null) {
            decision = Decision.APPROVE;
        }
        if (riskScore < 0 || riskScore > 100) {
            throw new IllegalArgumentException("El score de riesgo debe estar acotado entre 0 y 100 (recibido: " + riskScore + ")");
        }
        reasons = reasons != null ? List.copyOf(reasons) : Collections.emptyList();
    }

    /**
     * Resultado por defecto cuando no hay reglas activas o la lista de reglas está vacía:
     * Decisión APPROVE, score 0, sin motivos de riesgo.
     */
    public static RuleEvaluationResult defaultApproved() {
        return new RuleEvaluationResult(Decision.APPROVE, 0, Collections.emptyList(), 0, 0);
    }
}
