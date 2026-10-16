package com.pillapago.evaluation.engine;

import com.pillapago.evaluation.engine.exception.IncompatibleOperatorException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OperatorTest {

    @Nested
    @DisplayName("Resolución de operadores desde símbolos textuales")
    class FromSymbolTests {

        @ParameterizedTest
        @CsvSource({
                "'>', GREATER_THAN",
                "'<', LESS_THAN",
                "'>=', GREATER_OR_EQUAL",
                "'<=', LESS_OR_EQUAL",
                "'==', EQUALS",
                "'=', EQUALS",
                "'!=', NOT_EQUALS",
                "'in', IN",
                "'IN', IN"
        })
        void shouldResolveKnownSymbols(String symbol, Operator expected) {
            assertThat(Operator.fromSymbol(symbol)).isEqualTo(expected);
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   ", "regex", "like", "contains", "&&"})
        void shouldThrowOnInvalidSymbols(String invalidSymbol) {
            assertThatThrownBy(() -> Operator.fromSymbol(invalidSymbol))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldThrowOnNullSymbol() {
            assertThatThrownBy(() -> Operator.fromSymbol(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Operadores de igualdad (== y !=)")
    class EqualityTests {

        @Test
        void shouldCompareNumbersPrecisely() {
            assertThat(Operator.EQUALS.apply(100, 100)).isTrue();
            assertThat(Operator.EQUALS.apply(100, "100")).isTrue();
            assertThat(Operator.EQUALS.apply(new BigDecimal("150.00"), 150)).isTrue();
            assertThat(Operator.EQUALS.apply(new BigDecimal("150.00"), "150.0")).isTrue();
            assertThat(Operator.EQUALS.apply(100, 200)).isFalse();

            assertThat(Operator.NOT_EQUALS.apply(100, 200)).isTrue();
            assertThat(Operator.NOT_EQUALS.apply(100, "100.00")).isFalse();
        }

        @Test
        void shouldCompareStrings() {
            assertThat(Operator.EQUALS.apply("PEN", "PEN")).isTrue();
            assertThat(Operator.EQUALS.apply("pen", "PEN")).isTrue(); // Case-insensitive
            assertThat(Operator.EQUALS.apply("PEN", "USD")).isFalse();

            assertThat(Operator.NOT_EQUALS.apply("PEN", "USD")).isTrue();
            assertThat(Operator.NOT_EQUALS.apply("PEN", "pen")).isFalse();
        }

        @Test
        void shouldCompareBooleans() {
            assertThat(Operator.EQUALS.apply(true, true)).isTrue();
            assertThat(Operator.EQUALS.apply(true, false)).isFalse();
            assertThat(Operator.NOT_EQUALS.apply(true, false)).isTrue();
        }

        @Test
        void shouldHandleNulls() {
            assertThat(Operator.EQUALS.apply(null, null)).isTrue();
            assertThat(Operator.EQUALS.apply(null, "foo")).isFalse();
            assertThat(Operator.EQUALS.apply("foo", null)).isFalse();

            assertThat(Operator.NOT_EQUALS.apply(null, "foo")).isTrue();
            assertThat(Operator.NOT_EQUALS.apply(null, null)).isFalse();
        }
    }

    @Nested
    @DisplayName("Operadores relacionales (>, <, >=, <=)")
    class RelationalTests {

        @ParameterizedTest
        @CsvSource({
                "1500, 1000, true",
                "1000, 1000, false",
                "999, 1000, false",
                "1000.50, 1000, true"
        })
        void testGreaterThan(String actual, String target, boolean expected) {
            assertThat(Operator.GREATER_THAN.apply(new BigDecimal(actual), new BigDecimal(target)))
                    .isEqualTo(expected);
        }

        @ParameterizedTest
        @CsvSource({
                "999, 1000, true",
                "1000, 1000, false",
                "1500, 1000, false"
        })
        void testLessThan(String actual, String target, boolean expected) {
            assertThat(Operator.LESS_THAN.apply(new BigDecimal(actual), new BigDecimal(target)))
                    .isEqualTo(expected);
        }

        @ParameterizedTest
        @CsvSource({
                "1000, 1000, true",
                "1001, 1000, true",
                "999, 1000, false"
        })
        void testGreaterOrEqual(String actual, String target, boolean expected) {
            assertThat(Operator.GREATER_OR_EQUAL.apply(new BigDecimal(actual), new BigDecimal(target)))
                    .isEqualTo(expected);
        }

        @ParameterizedTest
        @CsvSource({
                "1000, 1000, true",
                "999, 1000, true",
                "1001, 1000, false"
        })
        void testLessOrEqual(String actual, String target, boolean expected) {
            assertThat(Operator.LESS_OR_EQUAL.apply(new BigDecimal(actual), new BigDecimal(target)))
                    .isEqualTo(expected);
        }

        @Test
        void shouldThrowOnBooleanComparisonWithRelationalOperator() {
            assertThatThrownBy(() -> Operator.GREATER_THAN.apply(true, false))
                    .isInstanceOf(IncompatibleOperatorException.class)
                    .hasMessageContaining("booleanos no admiten operadores relacionales");
        }

        @Test
        void shouldThrowOnNullValuesWithRelationalOperator() {
            assertThatThrownBy(() -> Operator.GREATER_THAN.apply(null, 100))
                    .isInstanceOf(IncompatibleOperatorException.class)
                    .hasMessageContaining("valores nulos");

            assertThatThrownBy(() -> Operator.LESS_THAN.apply(100, null))
                    .isInstanceOf(IncompatibleOperatorException.class)
                    .hasMessageContaining("valores nulos");
        }
    }

    @Nested
    @DisplayName("Operador de pertenencia (in)")
    class InOperatorTests {

        @Test
        void shouldFindStringInList() {
            List<String> list = List.of("PE", "US", "CO");
            assertThat(Operator.IN.apply("PE", list)).isTrue();
            assertThat(Operator.IN.apply("pe", list)).isTrue(); // Case-insensitive
            assertThat(Operator.IN.apply("AR", list)).isFalse();
        }

        @Test
        void shouldFindNumberInSet() {
            Set<Integer> set = Set.of(100, 200, 300);
            assertThat(Operator.IN.apply(200, set)).isTrue();
            assertThat(Operator.IN.apply(new BigDecimal("200.00"), set)).isTrue();
            assertThat(Operator.IN.apply(400, set)).isFalse();
        }

        @Test
        void shouldFindInCommaSeparatedString() {
            String csv = "PE, US, CO";
            assertThat(Operator.IN.apply("US", csv)).isTrue();
            assertThat(Operator.IN.apply("CL", csv)).isFalse();
        }

        @Test
        void shouldFindInArray() {
            String[] arr = new String[]{"VISA", "MASTERCARD"};
            assertThat(Operator.IN.apply("VISA", arr)).isTrue();
            assertThat(Operator.IN.apply("AMEX", arr)).isFalse();
        }

        @Test
        void shouldReturnFalseOnNullActualOrTarget() {
            assertThat(Operator.IN.apply(null, List.of("A", "B"))).isFalse();
            assertThat(Operator.IN.apply("A", null)).isFalse();
        }

        @Test
        void shouldThrowIfTargetIsNotCollectionOrCsvString() {
            assertThatThrownBy(() -> Operator.IN.apply("foo", 12345))
                    .isInstanceOf(IncompatibleOperatorException.class)
                    .hasMessageContaining("debe ser una colección");
        }
    }
}
