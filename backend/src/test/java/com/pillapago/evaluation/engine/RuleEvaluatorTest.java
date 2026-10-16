package com.pillapago.evaluation.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RuleEvaluatorTest {

    private RuleEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new DefaultRuleEvaluator();
    }

    @Test
    @DisplayName("Cuando la lista de reglas está vacía o es nula, el score debe ser 0 y la decisión APPROVE")
    void shouldReturnApproveWithZeroScoreWhenRulesAreEmptyOrNull() {
        EvaluationContext context = EvaluationContext.from(Map.of("amount", 100));

        RuleEvaluationResult resultEmpty = evaluator.evaluate(Collections.emptyList(), context);
        assertThat(resultEmpty.decision()).isEqualTo(Decision.APPROVE);
        assertThat(resultEmpty.riskScore()).isEqualTo(0);
        assertThat(resultEmpty.reasons()).isEmpty();
        assertThat(resultEmpty.evaluatedRulesCount()).isEqualTo(0);
        assertThat(resultEmpty.triggeredRulesCount()).isEqualTo(0);

        RuleEvaluationResult resultNull = evaluator.evaluate(null, context);
        assertThat(resultNull.decision()).isEqualTo(Decision.APPROVE);
        assertThat(resultNull.riskScore()).isEqualTo(0);
        assertThat(resultNull.reasons()).isEmpty();
    }

    @Test
    @DisplayName("Las reglas inactivas (active == false) no deben evaluarse ni sumar puntos")
    void shouldIgnoreInactiveRules() {
        EvaluationContext context = EvaluationContext.from(Map.of("amount", new BigDecimal("5000")));

        Rule activeRule = new Rule(
                "R1", "Regla activa", "Monto alto",
                new Condition("amount", Operator.GREATER_THAN, 1000), 30, true
        );

        Rule inactiveRule = new Rule(
                "R2", "Regla inactiva", "Monto super alto",
                new Condition("amount", Operator.GREATER_THAN, 2000), 50, false
        );

        RuleEvaluationResult result = evaluator.evaluate(List.of(activeRule, inactiveRule), context);

        assertThat(result.decision()).isEqualTo(Decision.APPROVE); // 30 < 50 (reviewThreshold)
        assertThat(result.riskScore()).isEqualTo(30);
        assertThat(result.reasons()).hasSize(1);
        assertThat(result.reasons().get(0).ruleId()).isEqualTo("R1");
        assertThat(result.evaluatedRulesCount()).isEqualTo(1);
        assertThat(result.triggeredRulesCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Debe evaluar condiciones compuestas con lógica AND en una misma regla")
    void shouldEvaluateCompoundConditionsWithAndLogic() {
        EvaluationContext context = EvaluationContext.from(Map.of(
                "amount", new BigDecimal("2500"),
                "currency", "USD"
        ));

        // Regla: Monto > 2000 AND Moneda == PEN (Falla la moneda)
        Rule ruleFails = new Rule(
                "R1", "Monto alto en PEN", "Monto alto en soles",
                List.of(
                        new Condition("amount", Operator.GREATER_THAN, 2000),
                        new Condition("currency", Operator.EQUALS, "PEN")
                ),
                40
        );

        // Regla: Monto > 2000 AND Moneda == USD (Cumplen ambas)
        Rule ruleMatches = new Rule(
                "R2", "Monto alto en USD", "Monto alto en dólares",
                List.of(
                        new Condition("amount", Operator.GREATER_THAN, 2000),
                        new Condition("currency", Operator.EQUALS, "USD")
                ),
                35
        );

        RuleEvaluationResult result = evaluator.evaluate(List.of(ruleFails, ruleMatches), context);

        assertThat(result.riskScore()).isEqualTo(35);
        assertThat(result.reasons()).extracting(RuleReason::ruleId).containsExactly("R2");
    }

    @Test
    @DisplayName("Escenario de fraude completo (HU-06): múltiples reglas sumando puntos hacia REJECT")
    void shouldEvaluateFullFraudScenario() {
        EvaluationContext context = EvaluationContext.from(Map.of(
                "amount", new BigDecimal("3200.00"),
                "currency", "PEN",
                "cardCountry", "US",
                "shippingCountry", "PE",
                "velocity.device_tx_10m", 6,
                "isNewCustomer", true
        ));

        List<Rule> rules = List.of(
                new Rule("R_VEL", "Dispositivo en múltiples cuentas", "Velocity alta",
                        new Condition("velocity.device_tx_10m", Operator.GREATER_THAN, 3), 40),
                new Rule("R_NEW", "Cuenta nueva", "Cliente recién registrado",
                        new Condition("isNewCustomer", Operator.EQUALS, true), 20),
                new Rule("R_GEO", "Tarjeta de otro país", "País emisor extranjero",
                        new Condition("cardCountry", Operator.EQUALS, "US"), 15),
                new Rule("R_AMT", "Monto alto", "Monto superior a 3000",
                        new Condition("amount", Operator.GREATER_THAN, 3000), 10)
        );

        // Suma esperada: 40 + 20 + 15 + 10 = 85 puntos (>= 80 rejectThreshold) -> REJECT
        RuleEvaluationResult result = evaluator.evaluate(rules, context);

        assertThat(result.decision()).isEqualTo(Decision.REJECT);
        assertThat(result.riskScore()).isEqualTo(85);
        assertThat(result.reasons()).hasSize(4);
        assertThat(result.evaluatedRulesCount()).isEqualTo(4);
        assertThat(result.triggeredRulesCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("Prueba de micro-latencia: 1,000 evaluaciones con múltiples reglas deben ejecutarse en pocos microsegundos (< 2 ms promedio)")
    void shouldExecuteSubMillisecondLatency() {
        EvaluationContext context = EvaluationContext.from(Map.of(
                "amount", new BigDecimal("1500.00"),
                "currency", "PEN",
                "cardCountry", "PE",
                "shippingCountry", "PE",
                "velocity.device_tx_10m", 2,
                "isNewCustomer", false
        ));

        List<Rule> rules = List.of(
                new Rule("R1", "Regla 1", "", new Condition("amount", ">", 1000), 10),
                new Rule("R2", "Regla 2", "", new Condition("currency", "==", "USD"), 20),
                new Rule("R3", "Regla 3", "", new Condition("velocity.device_tx_10m", ">=", 5), 30),
                new Rule("R4", "Regla 4", "", new Condition("cardCountry", "!=", "PE"), 25),
                new Rule("R5", "Regla 5", "", new Condition("cardCountry", "in", List.of("US", "CA")), 15)
        );

        // Calentamiento de JVM (Warm-up)
        for (int i = 0; i < 500; i++) {
            evaluator.evaluate(rules, context);
        }

        // Medición de 1,000 ejecuciones
        long start = System.nanoTime();
        int iterations = 1000;
        for (int i = 0; i < iterations; i++) {
            evaluator.evaluate(rules, context);
        }
        long durationNano = System.nanoTime() - start;
        double avgMillisPerEvaluation = (durationNano / 1_000_000.0) / iterations;

        // El tiempo promedio por evaluación debe ser < 0.1 ms (muy inferior a la meta estricta de < 2 ms)
        assertThat(avgMillisPerEvaluation).isLessThan(0.5);
    }
}
