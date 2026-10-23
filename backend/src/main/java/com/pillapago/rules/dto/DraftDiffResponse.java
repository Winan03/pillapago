package com.pillapago.rules.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * DTO que presenta el resumen de cambios (Diff) antes de confirmar la publicación de una versión (HU-08).
 * Sin emojis, puramente estructurado para UI profesional.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DraftDiffResponse {

    private String currentActiveVersion;
    private String nextProposedVersion;
    private int totalRulesInDraft;
    private int totalPointsInDraft;
    private List<RuleDiffItemDto> addedRules;
    private List<RuleDiffItemDto> modifiedRules;
    private List<RuleDiffItemDto> deactivatedRules;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RuleDiffItemDto {
        private UUID ruleId;
        private String name;
        private String description;
        private Integer points;
        private Integer previousPoints;
        private String changeSummary;
    }
}
