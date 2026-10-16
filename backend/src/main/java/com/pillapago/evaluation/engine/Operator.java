package com.pillapago.evaluation.engine;

import com.pillapago.evaluation.engine.exception.IncompatibleOperatorException;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

/**
 * Operadores soportados por el motor de reglas antifraude:
 * >, <, >=, <=, ==, !=, in.
 *
 * Implementa evaluación tipada, segura y de alta velocidad sin reflection ni SpEL.
 */
public enum Operator {
    GREATER_THAN(">"),
    LESS_THAN("<"),
    GREATER_OR_EQUAL(">="),
    LESS_OR_EQUAL("<="),
    EQUALS("=="),
    NOT_EQUALS("!="),
    IN("in");

    private final String symbol;

    Operator(String symbol) {
        this.symbol = symbol;
    }

    public String getSymbol() {
        return symbol;
    }

    /**
     * Resuelve un operador a partir de su símbolo textual.
     *
     * @param symbol Símbolo (ej: ">", "<=", "==", "!=", "in")
     * @return Instancia del operador correspondiente
     * @throws IllegalArgumentException si el símbolo no es reconocido
     */
    public static Operator fromSymbol(String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) {
            throw new IllegalArgumentException("El símbolo del operador no puede ser nulo ni vacío");
        }
        String cleanSymbol = symbol.trim().toLowerCase();
        for (Operator op : values()) {
            if (op.symbol.equalsIgnoreCase(cleanSymbol)) {
                return op;
            }
        }
        // Soporte adicional para variantes comunes como "=" en lugar de "=="
        if ("=".equals(cleanSymbol)) {
            return EQUALS;
        }
        throw new IllegalArgumentException("Operador desconocido: '" + symbol + "'. Operadores válidos: >, <, >=, <=, ==, !=, in");
    }

    /**
     * Aplica la operación de comparación entre el valor actual del contexto y el valor objetivo de la condición.
     *
     * @param actualValue Valor extraído del contexto
     * @param targetValue Valor objetivo definido en la regla
     * @return true si la condición se cumple; false en caso contrario
     * @throws IncompatibleOperatorException si el operador no es compatible con los tipos de datos
     */
    public boolean apply(Object actualValue, Object targetValue) {
        if (this == IN) {
            return evaluateIn(actualValue, targetValue);
        }
        if (this == EQUALS) {
            return evaluateEquals(actualValue, targetValue);
        }
        if (this == NOT_EQUALS) {
            return !evaluateEquals(actualValue, targetValue);
        }

        // Operadores relacionales: >, <, >=, <=
        return evaluateRelational(this, actualValue, targetValue);
    }

    private static boolean evaluateIn(Object actualValue, Object targetValue) {
        if (actualValue == null || targetValue == null) {
            return false;
        }

        Collection<?> collection = toCollection(targetValue);
        if (collection == null) {
            throw new IncompatibleOperatorException(
                    "in",
                    actualValue.getClass().getSimpleName(),
                    targetValue.getClass().getSimpleName(),
                    "El valor objetivo para el operador 'in' debe ser una colección, un arreglo o una lista separada por comas"
            );
        }

        // Si el valor actual es numérico, intentamos comparación numérica para evitar fallas por tipo (ej. Integer vs Long)
        if (isNumeric(actualValue)) {
            BigDecimal actualNum = toBigDecimal(actualValue);
            for (Object item : collection) {
                if (item != null && isNumeric(item) && actualNum.compareTo(toBigDecimal(item)) == 0) {
                    return true;
                }
            }
            return false;
        }

        // Comparación como String o equals directo
        for (Object item : collection) {
            if (item != null) {
                if (Objects.equals(actualValue, item) || actualValue.toString().equalsIgnoreCase(item.toString().trim())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Collection<?> toCollection(Object targetValue) {
        if (targetValue instanceof Collection<?> col) {
            return col;
        }
        if (targetValue instanceof Object[] arr) {
            return Arrays.asList(arr);
        }
        if (targetValue instanceof String str) {
            // Permite especificar "PE, US, CO" o "PE,US,CO"
            return Arrays.stream(str.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }
        return null;
    }

    private static boolean evaluateEquals(Object actualValue, Object targetValue) {
        if (actualValue == null && targetValue == null) {
            return true;
        }
        if (actualValue == null || targetValue == null) {
            return false;
        }

        // Si ambos son numéricos, comparar con BigDecimal (ej: 100 == 100.00)
        if (isNumeric(actualValue) && isNumeric(targetValue)) {
            return toBigDecimal(actualValue).compareTo(toBigDecimal(targetValue)) == 0;
        }

        // Si ambos son booleanos
        if (actualValue instanceof Boolean b1 && targetValue instanceof Boolean b2) {
            return b1.equals(b2);
        }

        // Comparación estándar por equals o cadenas
        if (Objects.equals(actualValue, targetValue)) {
            return true;
        }

        return actualValue.toString().equalsIgnoreCase(targetValue.toString());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean evaluateRelational(Operator op, Object actualValue, Object targetValue) {
        if (actualValue == null || targetValue == null) {
            throw new IncompatibleOperatorException(
                    op.symbol,
                    actualValue == null ? "null" : actualValue.getClass().getSimpleName(),
                    targetValue == null ? "null" : targetValue.getClass().getSimpleName(),
                    "No se pueden aplicar operadores relacionales (" + op.symbol + ") a valores nulos"
            );
        }

        // Booleanos no admiten operadores relacionales
        if (actualValue instanceof Boolean || targetValue instanceof Boolean) {
            throw new IncompatibleOperatorException(
                    op.symbol,
                    actualValue.getClass().getSimpleName(),
                    targetValue.getClass().getSimpleName(),
                    "Los valores booleanos no admiten operadores relacionales (" + op.symbol + ")"
            );
        }

        // Comparación numérica
        if (isNumeric(actualValue) && isNumeric(targetValue)) {
            int cmp = toBigDecimal(actualValue).compareTo(toBigDecimal(targetValue));
            return matchComparison(op, cmp);
        }

        // Comparación entre tipos comparables del mismo tipo (ej: String, Instant, etc.)
        if (actualValue instanceof Comparable c1 && actualValue.getClass().isInstance(targetValue)) {
            int cmp = c1.compareTo(targetValue);
            return matchComparison(op, cmp);
        }

        throw new IncompatibleOperatorException(
                op.symbol,
                actualValue.getClass().getSimpleName(),
                targetValue.getClass().getSimpleName(),
                "Los tipos no son mutuamente comparables con " + op.symbol
        );
    }

    private static boolean matchComparison(Operator op, int cmp) {
        return switch (op) {
            case GREATER_THAN -> cmp > 0;
            case LESS_THAN -> cmp < 0;
            case GREATER_OR_EQUAL -> cmp >= 0;
            case LESS_OR_EQUAL -> cmp <= 0;
            default -> false;
        };
    }

    public static boolean isNumeric(Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj instanceof Number) {
            return true;
        }
        if (obj instanceof String str) {
            try {
                new BigDecimal(str.trim());
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return false;
    }

    public static BigDecimal toBigDecimal(Object obj) {
        if (obj instanceof BigDecimal bd) {
            return bd;
        }
        if (obj instanceof Number num) {
            return new BigDecimal(num.toString());
        }
        if (obj instanceof String str) {
            return new BigDecimal(str.trim());
        }
        throw new IllegalArgumentException("No se puede convertir a BigDecimal: " + obj);
    }
}
