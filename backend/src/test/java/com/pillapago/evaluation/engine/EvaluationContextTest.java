package com.pillapago.evaluation.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluationContextTest {

    @Test
    @DisplayName("Debe construir contexto usando Builder de forma fluida")
    void shouldBuildUsingBuilder() {
        EvaluationContext context = EvaluationContext.builder()
                .put("amount", 250)
                .put("currency", "USD")
                .putAll(Map.of("country", "PE"))
                .put(null, "ignored")
                .putAll(null)
                .build();

        assertThat(context.has("amount")).isTrue();
        assertThat(context.has("currency")).isTrue();
        assertThat(context.has("country")).isTrue();
        assertThat(context.has("nonExistent")).isFalse();
        assertThat(context.has(null)).isFalse();
        assertThat(context.get("amount")).isEqualTo(250);
        assertThat(context.getAvailableFields()).contains("amount", "currency", "country");
    }

    @Test
    @DisplayName("Debe manejar contexto vacío")
    void shouldHandleEmptyContext() {
        EvaluationContext empty = EvaluationContext.empty();
        assertThat(empty.getAvailableFields()).isEmpty();
        assertThat(empty.has("any")).isFalse();
    }
}
