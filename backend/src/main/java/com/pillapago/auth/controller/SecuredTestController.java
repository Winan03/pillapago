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
}
