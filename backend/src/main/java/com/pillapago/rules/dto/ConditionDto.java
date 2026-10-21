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
public class ConditionDto {

    @NotBlank(message = "El nombre del campo es obligatorio")
    private String field;

    @NotBlank(message = "El operador es obligatorio")
    private String operator;

    @NotBlank(message = "El valor de comparación es obligatorio")
    private String value;
}
