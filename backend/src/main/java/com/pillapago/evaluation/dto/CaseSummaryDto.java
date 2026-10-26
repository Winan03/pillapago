package com.pillapago.evaluation.dto;

import com.pillapago.evaluation.domain.CaseResolution;
import com.pillapago.evaluation.domain.CaseStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Resumen de caso para la lista y cola de analistas de riesgo (HU-09).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaseSummaryDto {

    private UUID id;
    private Integer version;
    private CaseStatus status;
    private String priority;

    // Datos de la transacción
    private UUID transactionId;
    private String externalTransactionId;
    private BigDecimal amount;
    private String currency;
    private Integer riskScore;
    private String ruleSetVersion;
    private String paymentMethod;
    private String cardBin;
    private String cardLastFour;

    // Asignación y resolución
    private UUID assignedToId;
    private String assignedToName;
    private CaseResolution resolution;
    private UUID resolvedById;
    private String resolvedByName;
    private OffsetDateTime resolvedAt;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
