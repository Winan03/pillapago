package com.pillapago.tenant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * DTO de respuesta para la confirmación de revocación de una API key.
 * Entrega un mensaje amigable para que el frontend lo despliegue en un toast o alerta.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKeyRevokedResponse {

    private UUID id;
    private String status; // "REVOKED"
    private String userMessage;
}
