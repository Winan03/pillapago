package com.pillapago.tenant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * DTO para el listado seguro de API keys en el panel de administración.
 * NUNCA revela la clave secreta en texto plano ni el hash.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKeySummaryResponse {

    private UUID id;
    private String name;
    private String keyPrefix; // Ej. "pp_live_a1b2c3d4"
    private Boolean active;
    private OffsetDateTime createdAt;
    private OffsetDateTime lastUsedAt;
}
