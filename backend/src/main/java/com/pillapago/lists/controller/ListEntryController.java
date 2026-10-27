package com.pillapago.lists.controller;

import com.pillapago.lists.domain.ListEntryType;
import com.pillapago.lists.domain.ListType;
import com.pillapago.lists.dto.CreateListEntryRequest;
import com.pillapago.lists.dto.ListEntryResponseDto;
import com.pillapago.lists.service.ListService;
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
 * Controlador REST para la gestión de listas de bloqueo y de confianza (HU-13 / RF-09).
 * Accesible de forma estricta y exclusiva por analistas de riesgo (rol RISK_ANALYST).
 * Cualquier otro rol (como MERCHANT_ADMIN o AUDITOR) es rechazado con HTTP 403 Forbidden.
 */
@RestController
@RequestMapping("/v1/lists")
@RequiredArgsConstructor
@PreAuthorize("hasRole('RISK_ANALYST')")
public class ListEntryController {

    private final ListService listService;

    /**
     * Registra un nuevo identificador en la lista (BLOCK o ALLOW) con justificación obligatoria.
     * Responde HTTP 201 Created y aplica inmediatamente en la siguiente evaluación.
     */
    @PostMapping
    public ResponseEntity<ListEntryResponseDto> createEntry(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateListEntryRequest request
    ) {
        ListEntryResponseDto response = listService.createEntry(
                principal.getMerchantId(),
                principal.getId(),
                request
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Lista las entradas del comercio autenticado con filtros opcionales de tipo de lista y tipo de dato.
     */
    @GetMapping
    public ResponseEntity<List<ListEntryResponseDto>> listEntries(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) ListType listType,
            @RequestParam(required = false) ListEntryType entryType
    ) {
        List<ListEntryResponseDto> entries = listService.getEntries(
                principal.getMerchantId(),
                listType,
                entryType
        );
        return ResponseEntity.ok(entries);
    }

    /**
     * Obtiene el detalle de una entrada de lista específica del comercio.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ListEntryResponseDto> getEntry(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        ListEntryResponseDto entry = listService.getEntry(
                principal.getMerchantId(),
                id
        );
        return ResponseEntity.ok(entry);
    }

    /**
     * Elimina una entrada de la lista y la descarta inmediatamente de la memoria RAM.
     * Responde HTTP 204 No Content.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEntry(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        listService.deleteEntry(
                principal.getMerchantId(),
                id,
                principal.getId()
        );
        return ResponseEntity.noContent().build();
    }
}
