package com.pillapago.rules.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Representación inmutable de una regla preservada en el snapshot de versión (HU-08).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleSnapshotItemDto {
    private UUID ruleId;
    private String name;
    private String description;
    private Integer points;
    private Boolean active;
    private List<ConditionDto> conditions;
}
