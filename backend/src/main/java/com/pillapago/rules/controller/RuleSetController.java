package com.pillapago.rules.controller;

import com.pillapago.rules.dto.DraftDiffResponse;
import com.pillapago.rules.dto.PublishRuleSetRequest;
import com.pillapago.rules.dto.RollbackRuleSetRequest;
import com.pillapago.rules.dto.RuleSetVersionSummaryDto;
import com.pillapago.rules.service.RuleSetVersionService;
import com.pillapago.shared.security.UserPrincipal;
import com.pillapago.tenant.domain.User;
import com.pillapago.tenant.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controlador REST para el ciclo de vida y versionado inmutable de conjuntos de reglas (HU-08).
 * Permite publicación atómica, previsualización de diferencias (diff) y rollback con cero downtime.
 */
@RestController
@RequestMapping("/v1/rule-sets")
@RequiredArgsConstructor
public class RuleSetController {

    private final RuleSetVersionService ruleSetVersionService;
    private final UserRepository userRepository;

    @PostMapping("/publish")
    @PreAuthorize("hasAnyRole('MERCHANT_ADMIN', 'RISK_ANALYST')")
    public ResponseEntity<RuleSetVersionSummaryDto> publishVersion(
            @Valid @RequestBody(required = false) PublishRuleSetRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID merchantId = principal.getMerchantId();
        User currentUser = userRepository.findById(principal.getId()).orElse(null);

        RuleSetVersionSummaryDto summary = ruleSetVersionService.publishNewVersion(merchantId, currentUser, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(summary);
    }

    @PostMapping("/rollback")
    @PreAuthorize("hasAnyRole('MERCHANT_ADMIN', 'RISK_ANALYST')")
    public ResponseEntity<RuleSetVersionSummaryDto> rollbackVersion(
            @Valid @RequestBody RollbackRuleSetRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID merchantId = principal.getMerchantId();
        User currentUser = userRepository.findById(principal.getId()).orElse(null);

        RuleSetVersionSummaryDto summary = ruleSetVersionService.rollbackToVersion(merchantId, currentUser, request);
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/draft-diff")
    @PreAuthorize("hasAnyRole('MERCHANT_ADMIN', 'RISK_ANALYST')")
    public ResponseEntity<DraftDiffResponse> getDraftDiff(@AuthenticationPrincipal UserPrincipal principal) {
        UUID merchantId = principal.getMerchantId();
        DraftDiffResponse diff = ruleSetVersionService.calculateDraftDiff(merchantId);
        return ResponseEntity.ok(diff);
    }

    @GetMapping("/history")
    @PreAuthorize("hasAnyRole('MERCHANT_ADMIN', 'RISK_ANALYST')")
    public ResponseEntity<List<RuleSetVersionSummaryDto>> getHistory(@AuthenticationPrincipal UserPrincipal principal) {
        UUID merchantId = principal.getMerchantId();
        List<RuleSetVersionSummaryDto> history = ruleSetVersionService.getVersionHistory(merchantId);
        return ResponseEntity.ok(history);
    }
}
