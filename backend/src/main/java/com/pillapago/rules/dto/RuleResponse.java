package com.pillapago.rules.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleResponse {

    private UUID id;
    private String name;
    private String description;
    private Integer points;
    private Boolean active;
    private String status;
    private List<ConditionDto> conditions;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
