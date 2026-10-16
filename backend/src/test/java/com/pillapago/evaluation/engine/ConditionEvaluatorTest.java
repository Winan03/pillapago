package com.pillapago.evaluation.engine;

import com.pillapago.evaluation.engine.exception.IncompatibleOperatorException;
import com.pillapago.evaluation.engine.exception.InvalidConditionException;
import com.pillapago.evaluation.engine.exception.UnknownFieldException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConditionEvaluatorTest {

    private ConditionEvaluator evaluator;
    private EvaluationContext context;

    @BeforeEach
    void setUp() {
        evaluator = new ConditionEvaluator();
        context = EvaluationContext.from(Map.of(
                "amount", new BigDecimal("3200.00"),
                "currency", "PEN",
                "cardCountry", "US",
                "shippingCountry", "PE",
                "velocity.device_tx_10m", 5,
                "isNewCustomer", true
        ));
    }

    @Test
    @DisplayName("Debe evaluar exitosamente condiciones numéricas")
    void shouldEvaluateNumericCondition() {
        Condition cond = new Condition("amount", Operator.GREATER_THAN, new BigDecimal("3000.00"));
        assertThat(evaluator.evaluate(cond, context)).isTrue();

        Condition condFalse = new Condition("amount", Operator.LESS_THAN, 1000);
        assertThat(evaluator.evaluate(condFalse, context)).isFalse();
    }

    @Test
    @DisplayName("Debe evaluar métricas de velocidad")
    void shouldEvaluateVelocityMetric() {
        Condition velocityCond = new Condition("velocity.device_tx_10m", Operator.GREATER_OR_EQUAL, 5);
        assertThat(evaluator.evaluate(velocityCond, context)).isTrue();

        Condition velocityHigh = new Condition("velocity.device_tx_10m", Operator.GREATER_THAN, 10);
        assertThat(evaluator.evaluate(velocityHigh, context)).isFalse();
    }

    @Test
    @DisplayName("Debe evaluar condición con operador in")
    void shouldEvaluateInCondition() {
        Condition inCond = new Condition("cardCountry", Operator.IN, List.of("US", "CA", "MX"));
        assertThat(evaluator.evaluate(inCond, context)).isTrue();

        Condition notInCond = new Condition("cardCountry", Operator.IN, List.of("NG", "RU"));
        assertThat(evaluator.evaluate(notInCond, context)).isFalse();
    }

    @Test
    @DisplayName("Debe soportar nombres de campo independientemente de mayúsculas/minúsculas")
    void shouldResolveFieldCaseInsensitively() {
        Condition cond = new Condition("CARDCOUNTRY", Operator.EQUALS, "US");
        assertThat(evaluator.evaluate(cond, context)).isTrue();
    }

    @Test
    @DisplayName("Debe lanzar UnknownFieldException cuando el campo no existe en el contexto")
    void shouldThrowWhenFieldDoesNotExist() {
        Condition missingFieldCond = new Condition("non_existent_field", Operator.EQUALS, "foo");

        assertThatThrownBy(() -> evaluator.evaluate(missingFieldCond, context))
                .isInstanceOf(UnknownFieldException.class)
                .hasMessageContaining("non_existent_field")
                .satisfies(ex -> {
                    UnknownFieldException ufe = (UnknownFieldException) ex;
                    assertThat(ufe.getFieldName()).isEqualTo("non_existent_field");
                    assertThat(ufe.getAvailableFields()).contains("amount", "cardCountry");
                });
    }

    @Test
    @DisplayName("Debe lanzar IncompatibleOperatorException ante incompatibilidad de tipos")
    void shouldThrowOnIncompatibleOperator() {
        Condition badCond = new Condition("isNewCustomer", Operator.GREATER_THAN, false);

        assertThatThrownBy(() -> evaluator.evaluate(badCond, context))
                .isInstanceOf(IncompatibleOperatorException.class)
                .hasMessageContaining("booleanos no admiten operadores relacionales");
    }

    @Test
    @DisplayName("Debe validar que la condición no tenga valores nulos o campos vacíos")
    void shouldValidateConditionConstruction() {
        assertThatThrownBy(() -> new Condition(null, Operator.EQUALS, "val"))
                .isInstanceOf(InvalidConditionException.class);

        assertThatThrownBy(() -> new Condition("  ", Operator.EQUALS, "val"))
                .isInstanceOf(InvalidConditionException.class);

        assertThatThrownBy(() -> new Condition("field", (Operator) null, "val"))
                .isInstanceOf(InvalidConditionException.class);

        assertThatThrownBy(() -> new Condition("field", Operator.EQUALS, null))
                .isInstanceOf(InvalidConditionException.class);
    }
}
