package com.pillapago.tenant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Respuesta devuelta ÚNICAMENTE al momento de la creación de la API key.
 * Muestra la clave en texto plano una sola vez junto con un mensaje claro para el usuario.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKeyCreatedResponse {

    private UUID id;
    private String name;
    private String keyPrefix;
    private String apiKey; // Solo presente en esta respuesta
    private Boolean active;
    private OffsetDateTime createdAt;
    private String userMessage;
}
