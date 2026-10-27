package com.pillapago.evaluation.dto;

import com.pillapago.evaluation.domain.CaseResolution;
import com.pillapago.evaluation.domain.CaseStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Detalle completo de un caso de revisión manual (HU-09).
 * Expone score de riesgo, versión de reglas evaluada, motivos/reglas activadas y trazabilidad.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaseDetailDto {

    private UUID id;
    private Integer version;
    private CaseStatus status;
    private String priority;
    private String notes;

    // Transacción asociada
    private UUID transactionId;
    private String externalTransactionId;
    private BigDecimal amount;
    private String currency;
    private Integer riskScore;
    private String ruleSetVersion;
    private Boolean degraded;
    private Long latencyMs;
    private String paymentMethod;
    private String cardBin;
    private String cardLastFour;
    private String metadata;
    private OffsetDateTime transactionCreatedAt;

    // Reglas activadas y motivos
    private List<RuleReasonDto> triggeredRules;

    // Asignación y Resolución
    private UUID assignedToId;
    private String assignedToName;
    private String assignedToEmail;

    private CaseResolution resolution;
    private String resolutionNotes;
    private UUID resolvedById;
    private String resolvedByName;
    private String resolvedByEmail;
    private OffsetDateTime resolvedAt;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
