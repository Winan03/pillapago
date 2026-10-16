package com.pillapago.evaluation.engine;

import com.pillapago.evaluation.engine.exception.IncompatibleOperatorException;
import com.pillapago.evaluation.engine.exception.InvalidRuleException;
import com.pillapago.evaluation.engine.exception.RuleEngineException;
import com.pillapago.evaluation.engine.exception.UnknownFieldException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuleModelTest {

    @Test
    @DisplayName("Debe validar campos obligatorios y rangos de puntos en Rule")
    void shouldValidateRuleModel() {
        Condition cond = new Condition("amount", Operator.GREATER_THAN, 100);

        // ID nulo o vacío
        assertThatThrownBy(() -> new Rule(null, "Regla", "desc", List.of(cond), 20, true))
                .isInstanceOf(InvalidRuleException.class);
        assertThatThrownBy(() -> new Rule("  ", "Regla", "desc", List.of(cond), 20, true))
                .isInstanceOf(InvalidRuleException.class);

        // Nombre nulo o vacío
        assertThatThrownBy(() -> new Rule("R1", null, "desc", List.of(cond), 20, true))
                .isInstanceOf(InvalidRuleException.class);
        assertThatThrownBy(() -> new Rule("R1", "   ", "desc", List.of(cond), 20, true))
                .isInstanceOf(InvalidRuleException.class);

        // Condiciones nulas o vacías
        assertThatThrownBy(() -> new Rule("R1", "Regla", "desc", (List<Condition>) null, 20, true))
                .isInstanceOf(InvalidRuleException.class);
        assertThatThrownBy(() -> new Rule("R1", "Regla", "desc", Collections.emptyList(), 20, true))
                .isInstanceOf(InvalidRuleException.class);

        // Puntos fuera de rango [0, 100]
        assertThatThrownBy(() -> new Rule("R1", "Regla", "desc", List.of(cond), -1, true))
                .isInstanceOf(InvalidRuleException.class);
        assertThatThrownBy(() -> new Rule("R1", "Regla", "desc", List.of(cond), 101, true))
                .isInstanceOf(InvalidRuleException.class);

        // Constructores de conveniencia
        Rule r1 = new Rule("R1", "Regla 1", null, cond, 25);
        assertThat(r1.active()).isTrue();
        assertThat(r1.description()).isEqualTo("");

        Rule r2 = new Rule("R2", "Regla 2", "Desc", List.of(cond), 50);
        assertThat(r2.active()).isTrue();
    }

    @Test
    @DisplayName("Debe validar campos en RuleReason")
    void shouldValidateRuleReason() {
        assertThatThrownBy(() -> new RuleReason(null, "Regla", 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RuleReason("R1", null, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RuleReason("R1", "Regla", -5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RuleReason("R1", "Regla", 150))
                .isInstanceOf(IllegalArgumentException.class);

        RuleReason reason = new RuleReason(" R1 ", " Regla ", 30);
        assertThat(reason.ruleId()).isEqualTo("R1");
        assertThat(reason.ruleName()).isEqualTo("Regla");
        assertThat(reason.points()).isEqualTo(30);
    }

    @Test
    @DisplayName("Debe validar RuleEvaluationResult y sus valores límite")
    void shouldValidateRuleEvaluationResult() {
        assertThatThrownBy(() -> new RuleEvaluationResult(Decision.APPROVE, -1, List.of(), 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RuleEvaluationResult(Decision.APPROVE, 105, List.of(), 0, 0))
                .isInstanceOf(IllegalArgumentException.class);

        RuleEvaluationResult result = new RuleEvaluationResult(null, 50, null, 1, 1);
        assertThat(result.decision()).isEqualTo(Decision.APPROVE);
        assertThat(result.reasons()).isEmpty();
    }

    @Test
    @DisplayName("Debe validar constructores y getters de excepciones")
    void shouldValidateExceptions() {
        RuleEngineException baseEx = new RuleEngineException("Error", new RuntimeException("Causa"));
        assertThat(baseEx.getMessage()).isEqualTo("Error");
        assertThat(baseEx.getCause()).isNotNull();

        IncompatibleOperatorException incompEx = new IncompatibleOperatorException(">", "String", "Number", "tipos incompatibles");
        assertThat(incompEx.getOperator()).isEqualTo(">");
        assertThat(incompEx.getActualType()).isEqualTo("String");
        assertThat(incompEx.getTargetType()).isEqualTo("Number");

        IncompatibleOperatorException simpleIncomp = new IncompatibleOperatorException("Simple");
        assertThat(simpleIncomp.getOperator()).isNull();

        UnknownFieldException ufEx = new UnknownFieldException("campo", null);
        assertThat(ufEx.getAvailableFields()).isEmpty();
    }
}
