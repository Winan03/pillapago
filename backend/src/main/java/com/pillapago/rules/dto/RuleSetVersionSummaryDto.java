package com.pillapago.rules.dto;

import com.pillapago.rules.domain.RuleSetVersionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleSetVersionSummaryDto {
    private UUID id;
    private UUID merchantId;
    private String versionName;
    private Integer versionNumber;
    private String description;
    private RuleSetVersionStatus status;
    private Integer rulesCount;
    private String createdByUsername;
    private OffsetDateTime createdAt;
}
