package com.pillapago.rules.validator;

import com.pillapago.rules.dto.ConditionDto;
import com.pillapago.rules.dto.FieldMetadataDto;
import com.pillapago.rules.exception.InvalidRuleConditionException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/**
 * Validador del lenguaje acotado del motor antifraude (HU-06a / HU-07).
 * Garantiza que toda condición esté compuesta por un campo del catálogo permitido,
 * un operador compatible con el tipo de dato y un valor objetivo coherente.
 */
@Component
public class RuleLanguageValidator {

    public enum FieldType {
        NUMERIC,
        STRING,
        BOOLEAN
    }

    public record FieldDefinition(
            String field,
            String label,
            FieldType type,
            String category,
            Set<String> allowedOperators,
            String placeholder
    ) {}

    private static final Map<String, FieldDefinition> CATALOG = new LinkedHashMap<>();

    static {
        // 1. Campos de Transacción
        register(new FieldDefinition("amount", "Monto de la transacción", FieldType.NUMERIC, "Transacción",
                Set.of(">", "<", ">=", "<=", "==", "!="), "ej: 1500.00"));
        register(new FieldDefinition("currency", "Moneda", FieldType.STRING, "Transacción",
                Set.of("==", "!=", "in"), "ej: PEN, USD"));
        register(new FieldDefinition("cardCountry", "País emisor de tarjeta", FieldType.STRING, "Transacción",
                Set.of("==", "!=", "in"), "ej: PE, CL, US"));
        register(new FieldDefinition("ipCountry", "País de la IP", FieldType.STRING, "Transacción",
                Set.of("==", "!=", "in"), "ej: PE, US"));
        register(new FieldDefinition("deviceId", "Identificador de dispositivo", FieldType.STRING, "Transacción",
                Set.of("==", "!="), "ej: dev_xyz123"));
        register(new FieldDefinition("customerId", "Identificador de cliente", FieldType.STRING, "Transacción",
                Set.of("==", "!="), "ej: cust_999"));
        register(new FieldDefinition("isNewDevice", "Es dispositivo nuevo", FieldType.BOOLEAN, "Transacción",
                Set.of("==", "!="), "true o false"));
        register(new FieldDefinition("isNewCustomer", "Es cliente nuevo", FieldType.BOOLEAN, "Transacción",
                Set.of("==", "!="), "true o false"));

        // 2. Métricas de Velocity (Redis)
        register(new FieldDefinition("device_accounts_24h", "Cuentas distintas por dispositivo en 24h", FieldType.NUMERIC, "Velocidad (Redis)",
                Set.of(">", "<", ">=", "<=", "==", "!="), "ej: 3"));
        register(new FieldDefinition("device_txns_10m", "Compras por dispositivo en 10 min", FieldType.NUMERIC, "Velocidad (Redis)",
                Set.of(">", "<", ">=", "<=", "==", "!="), "ej: 5"));
        register(new FieldDefinition("ip_txns_10m", "Compras por IP en 10 min", FieldType.NUMERIC, "Velocidad (Redis)",
                Set.of(">", "<", ">=", "<=", "==", "!="), "ej: 10"));

        register(new FieldDefinition("shippingCountry", "País de envío", FieldType.STRING, "Transacción",
                Set.of("==", "!=", "in"), "ej: PE, US"));
        register(new FieldDefinition("country", "País de la operación", FieldType.STRING, "Transacción",
                Set.of("==", "!=", "in"), "ej: PE, CO, US"));
        register(new FieldDefinition("payment_method", "Método de pago", FieldType.STRING, "Transacción",
                Set.of("==", "!=", "in"), "ej: CREDIT_CARD, DEBIT_CARD"));
        register(new FieldDefinition("email_domain", "Dominio del correo", FieldType.STRING, "Transacción",
                Set.of("==", "!=", "in"), "ej: gmail.com"));
        register(new FieldDefinition("is_vpn", "Conexión vía VPN/Proxy", FieldType.BOOLEAN, "Seguridad / Red",
                Set.of("==", "!="), "true o false"));
        register(new FieldDefinition("is_emulator", "Ejecución en emulador", FieldType.BOOLEAN, "Seguridad / Dispositivo",
                Set.of("==", "!="), "true o false"));
        register(new FieldDefinition("velocity_ip_1h", "Transacciones por IP en 1h", FieldType.NUMERIC, "Velocidad (Redis)",
                Set.of(">", "<", ">=", "<=", "==", "!="), "ej: 10"));

        // Alias comunes para compatibilidad con prefijo de métrica
        CATALOG.put("velocity.device_accounts_24h", CATALOG.get("device_accounts_24h"));
        CATALOG.put("velocity.device_tx_10m", CATALOG.get("device_txns_10m"));
        CATALOG.put("velocity.device_txns_10m", CATALOG.get("device_txns_10m"));
        CATALOG.put("velocity.ip_tx_10m", CATALOG.get("ip_txns_10m"));
        CATALOG.put("velocity.ip_txns_10m", CATALOG.get("ip_txns_10m"));
        CATALOG.put("paymentmethod", CATALOG.get("payment_method"));
        CATALOG.put("isvpn", CATALOG.get("is_vpn"));
        CATALOG.put("isemulator", CATALOG.get("is_emulator"));
    }

    private static void register(FieldDefinition def) {
        CATALOG.put(def.field().toLowerCase(), def);
    }

    /**
     * Valida una lista de condiciones verificando catálogo, compatibilidad de operador y tipo.
     */
    public void validateConditions(List<ConditionDto> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            throw new InvalidRuleConditionException("La regla debe contener al menos una condición");
        }
        if (conditions.size() > 3) {
            throw new InvalidRuleConditionException("Una regla admite como máximo 3 condiciones combinadas por AND");
        }

        for (int i = 0; i < conditions.size(); i++) {
            ConditionDto cond = conditions.get(i);
            validateCondition(cond, i + 1);
        }
    }

    /**
     * Valida una condición individual.
     */
    public void validateCondition(ConditionDto cond, int index) {
        if (cond == null) {
            throw new InvalidRuleConditionException("La condición #" + index + " no puede ser nula");
        }

        String rawField = cond.getField() != null ? cond.getField().trim() : "";
        if (rawField.isEmpty()) {
            throw new InvalidRuleConditionException("El campo en la condición #" + index + " no puede ser vacío");
        }

        FieldDefinition fieldDef = CATALOG.get(rawField.toLowerCase());
        if (fieldDef == null) {
            throw new InvalidRuleConditionException(
                    "El campo '" + rawField + "' en la condición #" + index + " no existe en el catálogo permitido del motor antifraude");
        }

        String rawOperator = cond.getOperator() != null ? cond.getOperator().trim() : "";
        String normalizedOp = normalizeOperator(rawOperator);

        if (!fieldDef.allowedOperators().contains(normalizedOp)) {
            throw new InvalidRuleConditionException(
                    "El operador '" + rawOperator + "' es incompatible con el tipo " + fieldDef.type() +
                    " del campo '" + fieldDef.field() + "'. Operadores permitidos: " + String.join(", ", fieldDef.allowedOperators()));
        }

        String rawValue = cond.getValue() != null ? cond.getValue().trim() : "";
        if (rawValue.isEmpty()) {
            throw new InvalidRuleConditionException(
                    "El valor objetivo para el campo '" + fieldDef.field() + "' en la condición #" + index + " no puede estar vacío");
        }

        validateValueType(fieldDef, rawValue, normalizedOp, index);
    }

    private void validateValueType(FieldDefinition fieldDef, String value, String operator, int index) {
        switch (fieldDef.type()) {
            case NUMERIC -> {
                try {
                    new BigDecimal(value);
                } catch (NumberFormatException e) {
                    throw new InvalidRuleConditionException(
                            "El valor '" + value + "' en la condición #" + index + " no es un valor numérico válido para el campo numérico '" + fieldDef.field() + "'");
                }
            }
            case BOOLEAN -> {
                if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                    throw new InvalidRuleConditionException(
                            "El valor '" + value + "' en la condición #" + index + " debe ser un valor booleano ('true' o 'false') para el campo booleano '" + fieldDef.field() + "'");
                }
            }
            case STRING -> {
                if ("in".equals(operator)) {
                    // Validar que tenga al menos un elemento
                    String clean = value.replaceAll("[\\[\\]'\"]", "").trim();
                    if (clean.isEmpty()) {
                        throw new InvalidRuleConditionException(
                                "El operador 'in' en la condición #" + index + " requiere al menos un elemento en la lista");
                    }
                }
            }
        }
    }

    public String normalizeOperator(String operator) {
        if (operator == null) return "";
        String clean = operator.trim().toLowerCase();
        return switch (clean) {
            case ">", "greater_than" -> ">";
            case "<", "less_than" -> "<";
            case ">=", "greater_or_equal", "greater_than_or_equal" -> ">=";
            case "<=", "less_or_equal", "less_than_or_equal" -> "<=";
            case "==", "=", "equals", "equal" -> "==";
            case "!=", "not_equals", "not_equal" -> "!=";
            case "in" -> "in";
            default -> operator.trim();
        };
    }

    public FieldDefinition getFieldDefinition(String field) {
        if (field == null) return null;
        return CATALOG.get(field.trim().toLowerCase());
    }

    public List<FieldMetadataDto> getCatalogMetadata() {
        // Retornar lista sin duplicados de alias
        Set<String> seen = new HashSet<>();
        List<FieldMetadataDto> result = new ArrayList<>();
        for (FieldDefinition def : CATALOG.values()) {
            if (seen.add(def.field())) {
                result.add(FieldMetadataDto.builder()
                        .field(def.field())
                        .label(def.label())
                        .type(def.type().name())
                        .category(def.category())
                        .allowedOperators(new ArrayList<>(def.allowedOperators()))
                        .placeholder(def.placeholder())
                        .build());
            }
        }
        return result;
    }
}
