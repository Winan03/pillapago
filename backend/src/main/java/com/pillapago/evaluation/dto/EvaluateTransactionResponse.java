package com.pillapago.evaluation.dto;

import com.pillapago.evaluation.engine.Decision;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Contrato de respuesta para el Hot Path de evaluación de transacciones (POST /v1/transactions/evaluate).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvaluateTransactionResponse {

    private String transactionId;
    private Decision decision;
    private int riskScore;

    @Builder.Default
    private List<RuleReasonDto> reasons = new ArrayList<>();

    private String ruleSetVersion;
    private boolean degraded;
    private long latencyMs;
}
