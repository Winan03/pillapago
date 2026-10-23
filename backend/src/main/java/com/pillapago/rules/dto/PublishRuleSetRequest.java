package com.pillapago.rules.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublishRuleSetRequest {

    @Size(max = 1000, message = "La descripción no puede superar 1000 caracteres")
    private String description;
}
