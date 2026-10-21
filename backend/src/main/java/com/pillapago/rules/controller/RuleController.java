package com.pillapago.rules.controller;

import com.pillapago.rules.domain.RuleStatus;
import com.pillapago.rules.dto.CreateRuleRequest;
import com.pillapago.rules.dto.FieldMetadataDto;
import com.pillapago.rules.dto.RuleResponse;
import com.pillapago.rules.dto.UpdateRuleRequest;
import com.pillapago.rules.service.RuleService;
import com.pillapago.shared.security.UserPrincipal;
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
 * Controlador REST para la gestión de reglas antifraude (HU-07 / RF-10).
 * Protegido estrictamente con RBAC: Solo usuarios con rol RISK_ANALYST pueden acceder.
 */
@RestController
@RequestMapping("/v1/rules")
@RequiredArgsConstructor
@PreAuthorize("hasRole('RISK_ANALYST')")
public class RuleController {

    private final RuleService ruleService;

    @PostMapping
    public ResponseEntity<RuleResponse> createRule(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateRuleRequest request) {
        RuleResponse response = ruleService.createRule(principal.getMerchantId(), principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<RuleResponse>> getRules(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) RuleStatus status) {
        List<RuleResponse> rules = ruleService.getRules(principal.getMerchantId(), status);
        return ResponseEntity.ok(rules);
    }

    @GetMapping({"/catalog", "/fields"})
    public ResponseEntity<List<FieldMetadataDto>> getCatalog() {
        return ResponseEntity.ok(ruleService.getCatalog());
    }

    @GetMapping("/{id}")
    public ResponseEntity<RuleResponse> getRuleById(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        RuleResponse rule = ruleService.getRuleById(principal.getMerchantId(), id);
        return ResponseEntity.ok(rule);
    }

    @PutMapping("/{id}")
    public ResponseEntity<RuleResponse> updateRule(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRuleRequest request) {
        RuleResponse response = ruleService.updateRule(principal.getMerchantId(), id, request);
        return ResponseEntity.ok(response);
    }
}
