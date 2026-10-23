package com.pillapago.evaluation.controller;

import com.pillapago.evaluation.dto.EvaluateTransactionRequest;
import com.pillapago.evaluation.dto.EvaluateTransactionResponse;
import com.pillapago.evaluation.service.TransactionEvaluationService;
import com.pillapago.shared.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST para el Hot Path de evaluación de transacciones (HU-06c).
 * Autenticado vía cabecera X-API-Key con RBAC ROLE_API_CLIENT / ROLE_MERCHANT_ADMIN.
 */
@RestController
@RequestMapping("/v1/transactions")
@RequiredArgsConstructor
public class TransactionEvaluationController {

    private final TransactionEvaluationService evaluationService;

    @PostMapping("/evaluate")
    @PreAuthorize("hasAnyRole('API_CLIENT', 'MERCHANT_ADMIN')")
    public ResponseEntity<EvaluateTransactionResponse> evaluateTransaction(
            @Valid @RequestBody EvaluateTransactionRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        EvaluateTransactionResponse response = evaluationService.evaluate(
                request,
                principal.getMerchantId(),
                principal.getId()
        );

        return ResponseEntity.ok(response);
    }
}
