package com.pillapago.evaluation.engine;

import java.util.List;

/**
 * Puerto principal del motor de evaluación de reglas antifraude.
 * Contrato puro en Java sin dependencias de frameworks externos.
 */
public interface RuleEvaluator {

    /**
     * Evalúa una lista de reglas antifraude contra el contexto de una transacción y calcula la decisión final.
     *
     * @param rules Lista de reglas a evaluar (activas o inactivas)
     * @param context Contexto con atributos de la transacción y métricas de velocidad
     * @param thresholds Configuración de umbrales para la decisión
     * @return RuleEvaluationResult con score (0-100), decisión y motivos
     */
    RuleEvaluationResult evaluate(List<Rule> rules, EvaluationContext context, ThresholdConfig thresholds);

    /**
     * Evalúa una lista de reglas utilizando los umbrales de decisión por defecto.
     *
     * @param rules Lista de reglas a evaluar
     * @param context Contexto con atributos de la transacción
     * @return RuleEvaluationResult con score, decisión y motivos
     */
    default RuleEvaluationResult evaluate(List<Rule> rules, EvaluationContext context) {
        return evaluate(rules, context, ThresholdConfig.defaultConfig());
    }
}
