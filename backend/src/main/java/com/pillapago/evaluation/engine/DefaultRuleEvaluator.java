package com.pillapago.evaluation.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Implementación de referencia del motor de evaluación de reglas antifraude en Java 21 puro.
 * Diseñado para ejecución concurrente, inmutable, thread-safe y de latencia sub-milisegundo.
 */
public class DefaultRuleEvaluator implements RuleEvaluator {

    private final ConditionEvaluator conditionEvaluator;
    private final ScoreCalculator scoreCalculator;

    public DefaultRuleEvaluator(ConditionEvaluator conditionEvaluator, ScoreCalculator scoreCalculator) {
        this.conditionEvaluator = Objects.requireNonNull(conditionEvaluator, "ConditionEvaluator no puede ser nulo");
        this.scoreCalculator = Objects.requireNonNull(scoreCalculator, "ScoreCalculator no puede ser nulo");
    }

    public DefaultRuleEvaluator() {
        this(new ConditionEvaluator(), new ScoreCalculator());
    }

    @Override
    public RuleEvaluationResult evaluate(List<Rule> rules, EvaluationContext context, ThresholdConfig thresholds) {
        Objects.requireNonNull(context, "El contexto de evaluación no puede ser nulo");
        ThresholdConfig activeThresholds = thresholds != null ? thresholds : ThresholdConfig.defaultConfig();

        if (rules == null || rules.isEmpty()) {
            return RuleEvaluationResult.defaultApproved();
        }

        List<RuleReason> reasons = new ArrayList<>();
        int evaluatedCount = 0;

        for (Rule rule : rules) {
            if (rule == null || !rule.active()) {
                continue;
            }

            evaluatedCount++;
            boolean allConditionsMatch = true;

            for (Condition condition : rule.conditions()) {
                if (!conditionEvaluator.evaluate(condition, context)) {
                    allConditionsMatch = false;
                    break; // Cortocircuito rápido en conjunción AND
                }
            }

            if (allConditionsMatch) {
                reasons.add(new RuleReason(rule.id(), rule.name(), rule.points()));
            }
        }

        int score = scoreCalculator.calculateScore(reasons);
        Decision decision = scoreCalculator.resolveDecision(score, activeThresholds);

        return new RuleEvaluationResult(
                decision,
                score,
                Collections.unmodifiableList(reasons),
                evaluatedCount,
                reasons.size()
        );
    }
}
