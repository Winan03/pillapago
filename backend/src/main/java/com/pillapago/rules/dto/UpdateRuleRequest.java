package com.pillapago.rules.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateRuleRequest {

    @NotBlank(message = "El nombre de la regla es obligatorio")
    @Size(max = 255, message = "El nombre no puede exceder 255 caracteres")
    private String name;

    private String description;

    @NotNull(message = "Los puntos de riesgo son obligatorios")
    @Min(value = 0, message = "Los puntos deben ser mínimo 0")
    @Max(value = 100, message = "Los puntos deben ser máximo 100")
    private Integer points;

    @Builder.Default
    private Boolean active = true;

    @NotEmpty(message = "La regla debe tener al menos una condición")
    @Size(max = 3, message = "Una regla admite como máximo 3 condiciones combinadas por AND")
    @Valid
    private List<ConditionDto> conditions;
}
