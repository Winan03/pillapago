package com.pillapago.evaluation.dto;

import com.pillapago.evaluation.domain.CaseStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Parámetros de filtro para la cola de casos (HU-09).
 * Permite filtrar por antigüedad (días), montos mínimos/máximos y estado del caso.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaseFilterRequest {

    private Integer ageDays;
    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    private CaseStatus status;
}
