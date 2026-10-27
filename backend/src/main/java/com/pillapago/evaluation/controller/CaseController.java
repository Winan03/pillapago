package com.pillapago.evaluation.controller;

import com.pillapago.evaluation.domain.CaseStatus;
import com.pillapago.evaluation.dto.*;
import com.pillapago.evaluation.service.CaseService;
import com.pillapago.shared.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Controlador REST para la gestión de cola y resolución de casos de fraude (HU-09).
 * Accesible exclusivamente por usuarios con rol RISK_ANALYST o MERCHANT_ADMIN del comercio.
 */
@RestController
@RequestMapping("/v1/cases")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('RISK_ANALYST', 'MERCHANT_ADMIN')")
public class CaseController {

    private final CaseService caseService;

    /**
     * Lista los casos de revisión con filtros opcionales (antigüedad, monto, estado).
     */
    @GetMapping
    public ResponseEntity<List<CaseSummaryDto>> listCases(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) Integer ageDays,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(required = false) CaseStatus status) {

        CaseFilterRequest filter = CaseFilterRequest.builder()
                .ageDays(ageDays)
                .minAmount(minAmount)
                .maxAmount(maxAmount)
                .status(status)
                .build();

        List<CaseSummaryDto> cases = caseService.getCases(principal.getMerchantId(), filter);
        return ResponseEntity.ok(cases);
    }

    /**
     * Obtiene el detalle exhaustivo del caso (score, reglas activadas, versión de reglas).
     */
    @GetMapping("/{id}")
    public ResponseEntity<CaseDetailDto> getCaseDetail(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        CaseDetailDto detail = caseService.getCaseDetail(principal.getMerchantId(), id);
        return ResponseEntity.ok(detail);
    }

    /**
     * Asigna el caso al analista de riesgo autenticado.
     */
    @PostMapping("/{id}/claim")
    public ResponseEntity<CaseSummaryDto> claimCase(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        CaseSummaryDto claimed = caseService.claimCase(principal.getMerchantId(), id, principal.getId());
        return ResponseEntity.ok(claimed);
    }

    /**
     * Resuelve el caso aprobándolo o rechazándolo con justificación obligatoria.
     * Si dos analistas resuelven concurrentemente, JPA lanza OptimisticLockingFailureException -> 409 Conflict.
     */
    @PostMapping("/{id}/resolve")
    public ResponseEntity<CaseSummaryDto> resolveCase(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody ResolveCaseRequest request) {
        CaseSummaryDto resolved = caseService.resolveCase(principal.getMerchantId(), id, principal.getId(), request);
        return ResponseEntity.ok(resolved);
    }
}
