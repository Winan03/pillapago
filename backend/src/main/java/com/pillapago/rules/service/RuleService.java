package com.pillapago.rules.service;

import com.pillapago.rules.domain.RuleStatus;
import com.pillapago.rules.dto.CreateRuleRequest;
import com.pillapago.rules.dto.FieldMetadataDto;
import com.pillapago.rules.dto.RuleResponse;
import com.pillapago.rules.dto.UpdateRuleRequest;

import java.util.List;
import java.util.UUID;

public interface RuleService {

    RuleResponse createRule(UUID merchantId, UUID userId, CreateRuleRequest request);

    List<RuleResponse> getRules(UUID merchantId, RuleStatus status);

    RuleResponse getRuleById(UUID merchantId, UUID ruleId);

    RuleResponse updateRule(UUID merchantId, UUID ruleId, UpdateRuleRequest request);

    List<FieldMetadataDto> getCatalog();
}
