package com.pillapago.rules.service;

import com.pillapago.rules.domain.RuleConditionEntity;
import com.pillapago.rules.domain.RuleEntity;
import com.pillapago.rules.domain.RuleStatus;
import com.pillapago.rules.dto.*;
import com.pillapago.rules.exception.InvalidRuleConditionException;
import com.pillapago.rules.repository.RuleRepository;
import com.pillapago.rules.validator.RuleLanguageValidator;
import com.pillapago.shared.exception.ResourceNotFoundException;
import com.pillapago.tenant.domain.Merchant;
import com.pillapago.tenant.domain.User;
import com.pillapago.tenant.repository.MerchantRepository;
import com.pillapago.tenant.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RuleServiceImpl implements RuleService {

    private final RuleRepository ruleRepository;
    private final MerchantRepository merchantRepository;
    private final UserRepository userRepository;
    private final RuleLanguageValidator ruleLanguageValidator;

    @Override
    @Transactional
    public RuleResponse createRule(UUID merchantId, UUID userId, CreateRuleRequest request) {
        if (merchantId == null) {
            throw new IllegalArgumentException("merchantId no puede ser nulo");
        }

        // 1. Validar condiciones según lenguaje acotado del motor (HU-06a)
        ruleLanguageValidator.validateConditions(request.getConditions());

        // 2. Verificar duplicidad de nombre en borrador para el mismo comercio
        if (ruleRepository.existsByMerchantIdAndNameAndStatus(merchantId, request.getName().trim(), RuleStatus.DRAFT)) {
            throw new InvalidRuleConditionException(
                    "Ya existe una regla en borrador con el nombre '" + request.getName().trim() + "'");
        }

        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Comercio no encontrado: " + merchantId));

        User user = null;
        if (userId != null) {
            user = userRepository.findById(userId).orElse(null);
        }

        RuleEntity ruleEntity = RuleEntity.builder()
                .merchant(merchant)
                .name(request.getName().trim())
                .description(request.getDescription() != null ? request.getDescription().trim() : "")
                .points(request.getPoints())
                .active(request.getActive() != null ? request.getActive() : true)
                .status(RuleStatus.DRAFT)
                .createdBy(user)
                .conditions(new ArrayList<>())
                .build();

        // Agregar condiciones normalizadas
        for (int i = 0; i < request.getConditions().size(); i++) {
            ConditionDto condDto = request.getConditions().get(i);
            RuleLanguageValidator.FieldDefinition fieldDef = ruleLanguageValidator.getFieldDefinition(condDto.getField());

            RuleConditionEntity condEntity = RuleConditionEntity.builder()
                    .fieldName(fieldDef.field())
                    .operator(ruleLanguageValidator.normalizeOperator(condDto.getOperator()))
                    .targetValue(condDto.getValue().trim())
                    .valueType(fieldDef.type().name())
                    .orderIndex(i)
                    .build();

            ruleEntity.addCondition(condEntity);
        }

        RuleEntity saved = ruleRepository.save(ruleEntity);
        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RuleResponse> getRules(UUID merchantId, RuleStatus status) {
        if (merchantId == null) {
            throw new IllegalArgumentException("merchantId no puede ser nulo");
        }

        List<RuleEntity> entities;
        if (status != null) {
            entities = ruleRepository.findAllByMerchantIdAndStatusWithConditions(merchantId, status);
        } else {
            entities = ruleRepository.findAllByMerchantIdWithConditions(merchantId);
        }

        return entities.stream().map(this::mapToResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public RuleResponse getRuleById(UUID merchantId, UUID ruleId) {
        if (merchantId == null || ruleId == null) {
            throw new IllegalArgumentException("merchantId y ruleId no pueden ser nulos");
        }

        RuleEntity entity = ruleRepository.findByIdAndMerchantIdWithConditions(ruleId, merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Regla no encontrada con ID: " + ruleId));

        return mapToResponse(entity);
    }

    @Override
    @Transactional
    public RuleResponse updateRule(UUID merchantId, UUID ruleId, UpdateRuleRequest request) {
        if (merchantId == null || ruleId == null) {
            throw new IllegalArgumentException("merchantId y ruleId no pueden ser nulos");
        }

        RuleEntity entity = ruleRepository.findByIdAndMerchantIdWithConditions(ruleId, merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Regla no encontrada con ID: " + ruleId));

        // Solo se pueden editar reglas en estado DRAFT
        if (entity.getStatus() != RuleStatus.DRAFT) {
            throw new InvalidRuleConditionException(
                    "Solo se pueden editar reglas en estado borrador (DRAFT). Las reglas publicadas o archivadas son inmutables.");
        }

        // 1. Validar condiciones
        ruleLanguageValidator.validateConditions(request.getConditions());

        // 2. Verificar duplicidad de nombre si cambió
        if (ruleRepository.existsByMerchantIdAndNameAndStatusAndIdNot(merchantId, request.getName().trim(), RuleStatus.DRAFT, ruleId)) {
            throw new InvalidRuleConditionException(
                    "Ya existe otra regla en borrador con el nombre '" + request.getName().trim() + "'");
        }

        entity.setName(request.getName().trim());
        entity.setDescription(request.getDescription() != null ? request.getDescription().trim() : "");
        entity.setPoints(request.getPoints());
        if (request.getActive() != null) {
            entity.setActive(request.getActive());
        }

        // Reemplazar condiciones
        entity.getConditions().clear();
        for (int i = 0; i < request.getConditions().size(); i++) {
            ConditionDto condDto = request.getConditions().get(i);
            RuleLanguageValidator.FieldDefinition fieldDef = ruleLanguageValidator.getFieldDefinition(condDto.getField());

            RuleConditionEntity condEntity = RuleConditionEntity.builder()
                    .fieldName(fieldDef.field())
                    .operator(ruleLanguageValidator.normalizeOperator(condDto.getOperator()))
                    .targetValue(condDto.getValue().trim())
                    .valueType(fieldDef.type().name())
                    .orderIndex(i)
                    .build();

            entity.addCondition(condEntity);
        }

        RuleEntity updated = ruleRepository.save(entity);
        return mapToResponse(updated);
    }

    @Override
    public List<FieldMetadataDto> getCatalog() {
        return ruleLanguageValidator.getCatalogMetadata();
    }

    private RuleResponse mapToResponse(RuleEntity entity) {
        List<ConditionDto> conditionDtos = entity.getConditions().stream()
                .map(c -> ConditionDto.builder()
                        .field(c.getFieldName())
                        .operator(c.getOperator())
                        .value(c.getTargetValue())
                        .build())
                .toList();

        return RuleResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .points(entity.getPoints())
                .active(entity.getActive())
                .status(entity.getStatus().name())
                .conditions(conditionDtos)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
