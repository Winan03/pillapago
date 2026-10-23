package com.pillapago.rules.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pillapago.evaluation.engine.Rule;
import com.pillapago.evaluation.service.RuleEngineCacheService;
import com.pillapago.evaluation.service.RuleEngineCacheService.ActiveRuleSet;
import com.pillapago.rules.domain.*;
import com.pillapago.rules.dto.*;
import com.pillapago.rules.dto.DraftDiffResponse.RuleDiffItemDto;
import com.pillapago.rules.exception.InvalidRuleConditionException;
import com.pillapago.rules.repository.RuleRepository;
import com.pillapago.rules.repository.RuleSetVersionRepository;
import com.pillapago.shared.exception.ResourceNotFoundException;
import com.pillapago.tenant.domain.Merchant;
import com.pillapago.tenant.domain.User;
import com.pillapago.tenant.repository.MerchantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Servicio de negocio para la gestión, versionado inmutable, publicación y rollback de conjuntos de reglas (HU-08).
 * Garantiza recarga consistente de caché y activación atómica sin estados intermedios.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuleSetVersionService {

    private final RuleSetVersionRepository versionRepository;
    private final RuleRepository ruleRepository;
    private final MerchantRepository merchantRepository;
    private final RuleEngineCacheService ruleEngineCacheService;
    private final ObjectMapper objectMapper;

    /**
     * Publica una nueva versión inmutable vN a partir de las reglas activas del comercio.
     * La versión anterior pasa a ARCHIVED y se realiza swap atómico en la memoria del motor.
     */
    @Transactional
    public RuleSetVersionSummaryDto publishNewVersion(UUID merchantId, User currentUser, PublishRuleSetRequest request) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Comercio no encontrado: " + merchantId));

        // 1. Obtener todas las reglas activas configuradas para el comercio
        List<RuleEntity> activeRules = ruleRepository.findAllByMerchantIdWithConditions(merchantId).stream()
                .filter(r -> Boolean.TRUE.equals(r.getActive()))
                .toList();

        if (activeRules.isEmpty()) {
            throw new InvalidRuleConditionException("No es posible publicar una versión sin reglas activas configuradas.");
        }

        // 2. Determinar correlativo de la nueva versión (v1, v2, v3...)
        Integer maxVersion = versionRepository.findMaxVersionNumberByMerchantId(merchantId);
        int nextVersionNumber = (maxVersion != null ? maxVersion : 0) + 1;
        String nextVersionName = "v" + nextVersionNumber;

        // 3. Pasar la versión activa anterior a estado ARCHIVED
        int archivedCount = versionRepository.updateStatusByMerchantIdAndStatus(
                merchantId, RuleSetVersionStatus.ACTIVE, RuleSetVersionStatus.ARCHIVED);
        log.info("[RULE-SET-PUBLISH] Comercio {}: {} versión anterior archivada", merchantId, archivedCount);

        // 4. Construir snapshot inmutable de reglas
        List<RuleSnapshotItemDto> snapshotItems = activeRules.stream().map(rule -> {
            List<ConditionDto> conditionDtos = rule.getConditions().stream().map(c -> ConditionDto.builder()
                    .field(c.getFieldName())
                    .operator(c.getOperator())
                    .value(c.getTargetValue())
                    .build()).toList();

            return RuleSnapshotItemDto.builder()
                    .ruleId(rule.getId())
                    .name(rule.getName())
                    .description(rule.getDescription())
                    .points(rule.getPoints())
                    .active(rule.getActive())
                    .conditions(conditionDtos)
                    .build();
        }).toList();

        String snapshotJson;
        try {
            snapshotJson = objectMapper.writeValueAsString(snapshotItems);
        } catch (Exception e) {
            throw new RuntimeException("Error al serializar snapshot de reglas: " + e.getMessage(), e);
        }

        // 5. Persistir la nueva versión inmutable
        RuleSetVersionEntity newVersion = RuleSetVersionEntity.builder()
                .merchant(merchant)
                .versionName(nextVersionName)
                .versionNumber(nextVersionNumber)
                .description(request != null && request.getDescription() != null ? request.getDescription().trim() : "Publicación de reglas " + nextVersionName)
                .status(RuleSetVersionStatus.ACTIVE)
                .rulesSnapshot(snapshotJson)
                .rulesCount(activeRules.size())
                .createdBy(currentUser)
                .createdAt(OffsetDateTime.now())
                .build();

        newVersion = versionRepository.save(newVersion);

        // 6. Actualizar las reglas publicadas a status = PUBLISHED en la tabla rules
        for (RuleEntity rule : activeRules) {
            rule.setStatus(RuleStatus.PUBLISHED);
            ruleRepository.save(rule);
        }

        // 7. Compilación y Swap Atómico en Memoria (Caché del Motor en O(1))
        List<Rule> compiledRules = ruleEngineCacheService.compileFromSnapshot(snapshotJson);
        ruleEngineCacheService.updateActiveRuleSet(merchantId, new ActiveRuleSet(nextVersionName, nextVersionNumber, compiledRules));

        log.info("[RULE-SET-PUBLISH] Versión [{}] activada exitosamente para comercio {} con {} reglas",
                nextVersionName, merchantId, compiledRules.size());

        return mapToSummaryDto(newVersion);
    }

    /**
     * Reactiva una versión anterior archivada (Rollback).
     * La versión actual pasa a ARCHIVED y se restaura el snapshot histórico de reglas en memoria atómicamente.
     */
    @Transactional
    public RuleSetVersionSummaryDto rollbackToVersion(UUID merchantId, User currentUser, RollbackRuleSetRequest request) {
        String targetVersionName = request.getTargetVersion().trim();

        RuleSetVersionEntity targetVersion = versionRepository.findByMerchantIdAndVersionName(merchantId, targetVersionName)
                .orElseThrow(() -> new ResourceNotFoundException("La versión '" + targetVersionName + "' no existe para este comercio."));

        if (targetVersion.getStatus() == RuleSetVersionStatus.ACTIVE) {
            throw new InvalidRuleConditionException("La versión '" + targetVersionName + "' ya es la versión activa actualmente.");
        }

        // 1. Archivar la versión actualmente activa
        versionRepository.updateStatusByMerchantIdAndStatus(
                merchantId, RuleSetVersionStatus.ACTIVE, RuleSetVersionStatus.ARCHIVED);

        // 2. Reactivar la versión destino
        targetVersion.setStatus(RuleSetVersionStatus.ACTIVE);
        targetVersion = versionRepository.save(targetVersion);

        // 3. Swap Atómico en Memoria con las reglas históricas compiladas del snapshot
        List<Rule> restoredRules = ruleEngineCacheService.compileFromSnapshot(targetVersion.getRulesSnapshot());
        ruleEngineCacheService.updateActiveRuleSet(
                merchantId, new ActiveRuleSet(targetVersion.getVersionName(), targetVersion.getVersionNumber(), restoredRules));

        log.info("[RULE-SET-ROLLBACK] Rollback completado exitosamente a versión [{}] para comercio {}",
                targetVersionName, merchantId);

        return mapToSummaryDto(targetVersion);
    }

    /**
     * Calcula la diferencia (Diff) entre las reglas vigentes en borrador/edición y la última versión activa.
     */
    @Transactional(readOnly = true)
    public DraftDiffResponse calculateDraftDiff(UUID merchantId) {
        Optional<RuleSetVersionEntity> activeVersionOpt = versionRepository.findByMerchantIdAndStatus(merchantId, RuleSetVersionStatus.ACTIVE);
        List<RuleEntity> currentDbRules = ruleRepository.findAllByMerchantIdWithConditions(merchantId);

        String currentVersionName = activeVersionOpt.map(RuleSetVersionEntity::getVersionName).orElse("Ninguna");
        Integer maxVersion = versionRepository.findMaxVersionNumberByMerchantId(merchantId);
        int nextNumber = (maxVersion != null ? maxVersion : 0) + 1;
        String nextProposedVersion = "v" + nextNumber;

        List<RuleDiffItemDto> added = new ArrayList<>();
        List<RuleDiffItemDto> modified = new ArrayList<>();
        List<RuleDiffItemDto> deactivated = new ArrayList<>();

        int totalDraftPoints = 0;
        int totalActiveDraftRules = 0;

        for (RuleEntity r : currentDbRules) {
            if (Boolean.TRUE.equals(r.getActive())) {
                totalActiveDraftRules++;
                totalDraftPoints += (r.getPoints() != null ? r.getPoints() : 0);
            }
        }

        if (activeVersionOpt.isEmpty()) {
            // No hay versión previa: todas las reglas activas son nuevas
            for (RuleEntity r : currentDbRules) {
                if (Boolean.TRUE.equals(r.getActive())) {
                    added.add(RuleDiffItemDto.builder()
                            .ruleId(r.getId())
                            .name(r.getName())
                            .description(r.getDescription())
                            .points(r.getPoints())
                            .previousPoints(0)
                            .changeSummary("Nueva regla activa para primera versión")
                            .build());
                }
            }
        } else {
            // Comparar contra el snapshot de la versión activa
            Map<UUID, RuleSnapshotItemDto> snapshotMap = new HashMap<>();
            try {
                List<RuleSnapshotItemDto> items = objectMapper.readValue(
                        activeVersionOpt.get().getRulesSnapshot(), new TypeReference<>() {});
                snapshotMap = items.stream().collect(Collectors.toMap(RuleSnapshotItemDto::getRuleId, Function.identity()));
            } catch (Exception e) {
                log.warn("No se pudo parsear snapshot para diff: {}", e.getMessage());
            }

            Map<UUID, RuleEntity> currentDbMap = currentDbRules.stream()
                    .collect(Collectors.toMap(RuleEntity::getId, Function.identity()));

            // Detectar agregadas o modificadas
            for (RuleEntity rule : currentDbRules) {
                RuleSnapshotItemDto prev = snapshotMap.get(rule.getId());
                if (prev == null) {
                    if (Boolean.TRUE.equals(rule.getActive())) {
                        added.add(RuleDiffItemDto.builder()
                                .ruleId(rule.getId())
                                .name(rule.getName())
                                .description(rule.getDescription())
                                .points(rule.getPoints())
                                .previousPoints(0)
                                .changeSummary("Nueva regla agregada")
                                .build());
                    }
                } else {
                    if (!Boolean.TRUE.equals(rule.getActive())) {
                        deactivated.add(RuleDiffItemDto.builder()
                                .ruleId(rule.getId())
                                .name(rule.getName())
                                .description(rule.getDescription())
                                .points(0)
                                .previousPoints(prev.getPoints())
                                .changeSummary("Regla desactivada")
                                .build());
                    } else if (!Objects.equals(rule.getPoints(), prev.getPoints())
                            || !Objects.equals(rule.getName(), prev.getName())
                            || conditionsChanged(rule.getConditions(), prev.getConditions())) {
                        modified.add(RuleDiffItemDto.builder()
                                .ruleId(rule.getId())
                                .name(rule.getName())
                                .description(rule.getDescription())
                                .points(rule.getPoints())
                                .previousPoints(prev.getPoints())
                                .changeSummary("Modificación de puntaje o condiciones (anterior: " + prev.getPoints() + " pts, actual: " + rule.getPoints() + " pts)")
                                .build());
                    }
                }
            }

            // Detectar eliminadas de la base de datos
            for (Map.Entry<UUID, RuleSnapshotItemDto> entry : snapshotMap.entrySet()) {
                if (!currentDbMap.containsKey(entry.getKey())) {
                    RuleSnapshotItemDto prev = entry.getValue();
                    deactivated.add(RuleDiffItemDto.builder()
                            .ruleId(prev.getRuleId())
                            .name(prev.getName())
                            .description(prev.getDescription())
                            .points(0)
                            .previousPoints(prev.getPoints())
                            .changeSummary("Regla eliminada del catálogo")
                            .build());
                }
            }
        }

        return DraftDiffResponse.builder()
                .currentActiveVersion(currentVersionName)
                .nextProposedVersion(nextProposedVersion)
                .totalRulesInDraft(totalActiveDraftRules)
                .totalPointsInDraft(totalDraftPoints)
                .addedRules(added)
                .modifiedRules(modified)
                .deactivatedRules(deactivated)
                .build();
    }

    /**
     * Lista el historial completo de versiones de reglas del comercio.
     */
    @Transactional(readOnly = true)
    public List<RuleSetVersionSummaryDto> getVersionHistory(UUID merchantId) {
        return versionRepository.findAllByMerchantIdOrderByVersionNumberDesc(merchantId).stream()
                .map(this::mapToSummaryDto)
                .toList();
    }

    private boolean conditionsChanged(List<RuleConditionEntity> current, List<ConditionDto> previous) {
        if (current == null && previous == null) return false;
        if (current == null || previous == null) return true;
        if (current.size() != previous.size()) return true;

        for (int i = 0; i < current.size(); i++) {
            RuleConditionEntity c = current.get(i);
            ConditionDto p = previous.get(i);
            if (!Objects.equals(c.getFieldName(), p.getField())
                    || !Objects.equals(c.getOperator(), p.getOperator())
                    || !Objects.equals(c.getTargetValue(), p.getValue())) {
                return true;
            }
        }
        return false;
    }

    private RuleSetVersionSummaryDto mapToSummaryDto(RuleSetVersionEntity entity) {
        return RuleSetVersionSummaryDto.builder()
                .id(entity.getId())
                .merchantId(entity.getMerchant().getId())
                .versionName(entity.getVersionName())
                .versionNumber(entity.getVersionNumber())
                .description(entity.getDescription())
                .status(entity.getStatus())
                .rulesCount(entity.getRulesCount())
                .createdByUsername(entity.getCreatedBy() != null ? entity.getCreatedBy().getUsername() : "sistema")
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
