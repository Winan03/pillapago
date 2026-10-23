package com.pillapago.evaluation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Payload de entrada para el endpoint de evaluación de transacciones (POST /v1/transactions/evaluate).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvaluateTransactionRequest {

    @NotBlank(message = "El identificador de la transacción es obligatorio")
    private String transactionId;

    @NotNull(message = "El monto de la transacción es obligatorio")
    @Positive(message = "El monto debe ser mayor a cero")
    private BigDecimal amount;

    @NotBlank(message = "La moneda es obligatoria")
    @Pattern(regexp = "^[A-Z]{3}$", message = "La moneda debe tener formato ISO 4217 de 3 letras (ej. PEN, USD)")
    private String currency;

    private String paymentMethod;

    private String cardBin;

    private String cardLastFour;

    private String cardFingerprint;

    private String customerEmail;

    private String customerId;

    private String ipAddress;

    private String deviceId;

    private Map<String, Object> metadata;
}
