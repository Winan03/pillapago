package com.pillapago.evaluation.dto;

import lombok.*;

/**
 * Representa una regla activada en la respuesta de evaluación de la transacción.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RuleReasonDto {

    private String ruleName;
    private int points;
    private String description;
}
