package com.pillapago.rules.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RollbackRuleSetRequest {

    @NotBlank(message = "El nombre de la versión destino es obligatorio (ej. v1)")
    private String targetVersion;
}
