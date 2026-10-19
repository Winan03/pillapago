package com.pillapago.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Controlador de prueba para verificar autorización RBAC con @PreAuthorize.
 */
@RestController
@RequestMapping("/v1/test")
public class SecuredTestController {

    @GetMapping("/admin-only")
    @PreAuthorize("hasRole('MERCHANT_ADMIN')")
    public ResponseEntity<Map<String, String>> adminEndpoint() {
        return ResponseEntity.ok(Map.of("message", "Acceso concedido a MERCHANT_ADMIN"));
    }

    @GetMapping("/analyst-only")
    @PreAuthorize("hasRole('RISK_ANALYST')")
    public ResponseEntity<Map<String, String>> analystEndpoint() {
        return ResponseEntity.ok(Map.of("message", "Acceso concedido a RISK_ANALYST"));
    }

    @GetMapping("/auditor-only")
    @PreAuthorize("hasRole('AUDITOR')")
    public ResponseEntity<Map<String, String>> auditorEndpoint() {
        return ResponseEntity.ok(Map.of("message", "Acceso concedido a AUDITOR"));
    }

    @GetMapping("/api-key-secured")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> apiKeySecuredEndpoint(
            @org.springframework.security.core.annotation.AuthenticationPrincipal com.pillapago.shared.security.UserPrincipal principal
    ) {
        return ResponseEntity.ok(Map.of(
                "message", "Autenticación exitosa mediante API Key",
                "merchantId", principal.getMerchantId() != null ? principal.getMerchantId().toString() : "null",
                "status", "ACTIVE"
        ));
    }
}
