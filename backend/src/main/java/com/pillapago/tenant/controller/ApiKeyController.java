package com.pillapago.tenant.controller;

import com.pillapago.shared.security.UserPrincipal;
import com.pillapago.tenant.dto.ApiKeyCreatedResponse;
import com.pillapago.tenant.dto.ApiKeyRevokedResponse;
import com.pillapago.tenant.dto.ApiKeySummaryResponse;
import com.pillapago.tenant.dto.CreateApiKeyRequest;
import com.pillapago.tenant.service.ApiKeyService;
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
 * Controlador REST para la gestión de API Keys del comercio (HU-05).
 * Protegido estrictamente con RBAC (@PreAuthorize) solo para MERCHANT_ADMIN.
 */
@RestController
@RequestMapping("/v1/api-keys")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MERCHANT_ADMIN')")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    /**
     * Genera una nueva API Key para el comercio autenticado.
     * Muestra la clave en claro una única vez en la respuesta.
     */
    @PostMapping
    public ResponseEntity<ApiKeyCreatedResponse> createApiKey(
            @Valid @RequestBody CreateApiKeyRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        ApiKeyCreatedResponse response = apiKeyService.createApiKey(request, principal.getMerchantId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Lista las claves de API del comercio autenticado.
     * Oculta el secreto en texto plano y el hash, mostrando solo nombre, prefijo y fechas.
     */
    @GetMapping
    public ResponseEntity<List<ApiKeySummaryResponse>> listApiKeys(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<ApiKeySummaryResponse> response = apiKeyService.listApiKeys(principal.getMerchantId());
        return ResponseEntity.ok(response);
    }

    /**
     * Revoca inmediatamente la API Key especificada.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiKeyRevokedResponse> revokeApiKey(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        ApiKeyRevokedResponse response = apiKeyService.revokeApiKey(id, principal.getMerchantId());
        return ResponseEntity.ok(response);
    }
}
