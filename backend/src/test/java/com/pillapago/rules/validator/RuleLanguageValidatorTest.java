package com.pillapago.rules.validator;

import com.pillapago.rules.dto.ConditionDto;
import com.pillapago.rules.exception.InvalidRuleConditionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuleLanguageValidatorTest {

    private RuleLanguageValidator validator;

    @BeforeEach
    void setUp() {
        validator = new RuleLanguageValidator();
    }

    @Test
    @DisplayName("Debe validar exitosamente condiciones válidas con operadores numéricos")
    void shouldValidateValidNumericConditions() {
        List<ConditionDto> conditions = List.of(
                ConditionDto.builder().field("device_accounts_24h").operator(">").value("3").build(),
                ConditionDto.builder().field("amount").operator("<=").value("5000.50").build(),
                ConditionDto.builder().field("velocity_ip_1h").operator("==").value("10").build()
        );

        assertThatCode(() -> validator.validateConditions(conditions))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Debe validar exitosamente condiciones válidas con operadores de texto y lista")
    void shouldValidateValidStringConditions() {
        List<ConditionDto> conditions = List.of(
                ConditionDto.builder().field("country").operator("in").value("PE,CO,MX").build(),
                ConditionDto.builder().field("payment_method").operator("==").value("CREDIT_CARD").build(),
                ConditionDto.builder().field("email_domain").operator("in").value("gmail.com,hotmail.com").build()
        );

        assertThatCode(() -> validator.validateConditions(conditions))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Debe validar exitosamente condición booleana")
    void shouldValidateValidBooleanCondition() {
        List<ConditionDto> conditions = List.of(
                ConditionDto.builder().field("is_vpn").operator("==").value("true").build(),
                ConditionDto.builder().field("is_emulator").operator("!=").value("FALSE").build()
        );

        assertThatCode(() -> validator.validateConditions(conditions))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Debe rechazar lista vacía de condiciones")
    void shouldRejectEmptyConditions() {
        assertThatThrownBy(() -> validator.validateConditions(Collections.emptyList()))
                .isInstanceOf(InvalidRuleConditionException.class)
                .hasMessageContaining("al menos una condición");

        assertThatThrownBy(() -> validator.validateConditions(null))
                .isInstanceOf(InvalidRuleConditionException.class)
                .hasMessageContaining("al menos una condición");
    }

    @Test
    @DisplayName("Debe rechazar más de 3 condiciones (requisito de hasta 3 condiciones AND)")
    void shouldRejectMoreThanThreeConditions() {
        List<ConditionDto> conditions = List.of(
                ConditionDto.builder().field("amount").operator(">").value("100").build(),
                ConditionDto.builder().field("velocity_ip_1h").operator(">").value("2").build(),
                ConditionDto.builder().field("is_vpn").operator("==").value("true").build(),
                ConditionDto.builder().field("country").operator("==").value("PE").build()
        );

        assertThatThrownBy(() -> validator.validateConditions(conditions))
                .isInstanceOf(InvalidRuleConditionException.class)
                .hasMessageContaining("máximo 3 condiciones");
    }

    @Test
    @DisplayName("Debe rechazar campo inexistente o no soportado por el motor (código 400)")
    void shouldRejectUnknownField() {
        List<ConditionDto> conditions = List.of(
                ConditionDto.builder().field("campo_inventado_xyz").operator("==").value("123").build()
        );

        assertThatThrownBy(() -> validator.validateConditions(conditions))
                .isInstanceOf(InvalidRuleConditionException.class)
                .hasMessageContaining("no existe en el catálogo permitido")
                .hasMessageContaining("campo_inventado_xyz");
    }

    @Test
    @DisplayName("Debe rechazar operador no soportado por el motor (ej: contains)")
    void shouldRejectUnsupportedOperator() {
        List<ConditionDto> conditions = List.of(
                ConditionDto.builder().field("country").operator("contains").value("PE").build()
        );

        assertThatThrownBy(() -> validator.validateConditions(conditions))
                .isInstanceOf(InvalidRuleConditionException.class)
                .hasMessageContaining("incompatible con el tipo");
    }

    @Test
    @DisplayName("Debe rechazar operador incompatible con tipo string (ej: > en country)")
    void shouldRejectIncompatibleOperatorOnStringField() {
        List<ConditionDto> conditions = List.of(
                ConditionDto.builder().field("country").operator(">").value("PE").build()
        );

        assertThatThrownBy(() -> validator.validateConditions(conditions))
                .isInstanceOf(InvalidRuleConditionException.class)
                .hasMessageContaining("incompatible con el tipo STRING");
    }

    @Test
    @DisplayName("Debe rechazar operador incompatible con tipo boolean (ej: > en is_vpn)")
    void shouldRejectIncompatibleOperatorOnBooleanField() {
        List<ConditionDto> conditions = List.of(
                ConditionDto.builder().field("is_vpn").operator(">").value("true").build()
        );

        assertThatThrownBy(() -> validator.validateConditions(conditions))
                .isInstanceOf(InvalidRuleConditionException.class)
                .hasMessageContaining("incompatible con el tipo BOOLEAN");
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "cien_soles", "12,34", "--10"})
    @DisplayName("Debe rechazar valor no numérico en campo NUMERIC")
    void shouldRejectInvalidNumericValue(String invalidValue) {
        List<ConditionDto> conditions = List.of(
                ConditionDto.builder().field("device_accounts_24h").operator(">").value(invalidValue).build()
        );

        assertThatThrownBy(() -> validator.validateConditions(conditions))
                .isInstanceOf(InvalidRuleConditionException.class)
                .hasMessageContaining("valor numérico válido");
    }

    @ParameterizedTest
    @ValueSource(strings = {"quizas", "talvez", "1", "0", "si"})
    @DisplayName("Debe rechazar valor no booleano en campo BOOLEAN")
    void shouldRejectInvalidBooleanValue(String invalidValue) {
        List<ConditionDto> conditions = List.of(
                ConditionDto.builder().field("is_vpn").operator("==").value(invalidValue).build()
        );

        assertThatThrownBy(() -> validator.validateConditions(conditions))
                .isInstanceOf(InvalidRuleConditionException.class)
                .hasMessageContaining("valor booleano ('true' o 'false')");
    }

    @Test
    @DisplayName("Debe retornar la lista completa de metadatos de campos para el frontend")
    void shouldReturnAvailableFieldsMetadata() {
        var metadata = validator.getCatalogMetadata();
        assertThat(metadata).isNotEmpty();
        assertThat(metadata).anyMatch(f -> f.getField().equals("device_accounts_24h") && f.getType().equals("NUMERIC"));
        assertThat(metadata).anyMatch(f -> f.getField().equals("is_vpn") && f.getType().equals("BOOLEAN"));
        assertThat(metadata).anyMatch(f -> f.getField().equals("country") && f.getType().equals("STRING"));
    }
}
