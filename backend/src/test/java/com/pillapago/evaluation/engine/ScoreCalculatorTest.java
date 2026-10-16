package com.pillapago.evaluation.engine;

import com.pillapago.evaluation.engine.exception.InvalidThresholdException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScoreCalculatorTest {

    private ScoreCalculator scoreCalculator;

    @BeforeEach
    void setUp() {
        scoreCalculator = new ScoreCalculator();
    }

    @Test
    @DisplayName("Score debe ser 0 cuando la lista de motivos es nula o vacía")
    void shouldReturnZeroScoreWhenReasonsEmptyOrNull() {
        assertThat(scoreCalculator.calculateScore(null)).isEqualTo(0);
        assertThat(scoreCalculator.calculateScore(Collections.emptyList())).isEqualTo(0);
    }

    @Test
    @DisplayName("Debe sumar correctamente los puntos de reglas activadas")
    void shouldSumPointsCorrectly() {
        List<RuleReason> reasons = List.of(
                new RuleReason("R1", "Regla 1", 20),
                new RuleReason("R2", "Regla 2", 30),
                new RuleReason("R3", "Regla 3", 15)
        );

        assertThat(scoreCalculator.calculateScore(reasons)).isEqualTo(65);
    }

    @Test
    @DisplayName("Score debe tener un tope estricto de 100 puntos aunque la suma supere 100")
    void shouldCapScoreAtMax100() {
        List<RuleReason> reasons = List.of(
                new RuleReason("R1", "Monto muy alto", 50),
                new RuleReason("R2", "Dispositivo sospechoso", 40),
                new RuleReason("R3", "Velocidad extrema", 30)
        ); // Total = 120

        assertThat(scoreCalculator.calculateScore(reasons)).isEqualTo(100);
    }

    @ParameterizedTest(name = "Score {0} con umbrales [50, 80] debe resultar en {1}")
    @CsvSource({
            "0, APPROVE",
            "10, APPROVE",
            "49, APPROVE",
            "50, REVIEW",
            "60, REVIEW",
            "79, REVIEW",
            "80, REJECT",
            "85, REJECT",
            "100, REJECT"
    })
    void shouldResolveDecisionWithDefaultThresholds(int score, Decision expectedDecision) {
        ThresholdConfig thresholds = ThresholdConfig.defaultConfig();
        Decision decision = scoreCalculator.resolveDecision(score, thresholds);
        assertThat(decision).isEqualTo(expectedDecision);
    }

    @Test
    @DisplayName("Debe respetar umbrales configurables personalizados")
    void shouldRespectCustomThresholds() {
        ThresholdConfig customThresholds = new ThresholdConfig(30, 60);

        assertThat(scoreCalculator.resolveDecision(29, customThresholds)).isEqualTo(Decision.APPROVE);
        assertThat(scoreCalculator.resolveDecision(30, customThresholds)).isEqualTo(Decision.REVIEW);
        assertThat(scoreCalculator.resolveDecision(59, customThresholds)).isEqualTo(Decision.REVIEW);
        assertThat(scoreCalculator.resolveDecision(60, customThresholds)).isEqualTo(Decision.REJECT);
    }

    @Test
    @DisplayName("Debe lanzar InvalidThresholdException si los umbrales violan las reglas de negocio")
    void shouldValidateThresholdConfig() {
        // reviewThreshold >= rejectThreshold
        assertThatThrownBy(() -> new ThresholdConfig(50, 50))
                .isInstanceOf(InvalidThresholdException.class)
                .hasMessageContaining("estrictamente mayor");

        assertThatThrownBy(() -> new ThresholdConfig(80, 50))
                .isInstanceOf(InvalidThresholdException.class)
                .hasMessageContaining("estrictamente mayor");

        // Fuera de rango [0, 100]
        assertThatThrownBy(() -> new ThresholdConfig(-5, 80))
                .isInstanceOf(InvalidThresholdException.class);

        assertThatThrownBy(() -> new ThresholdConfig(50, 105))
                .isInstanceOf(InvalidThresholdException.class);
    }
}
