package com.pillapago.evaluation.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pillapago.evaluation.engine.Condition;
import com.pillapago.evaluation.engine.Operator;
import com.pillapago.evaluation.engine.Rule;
import com.pillapago.rules.domain.RuleConditionEntity;
import com.pillapago.rules.domain.RuleEntity;
import com.pillapago.rules.domain.RuleSetVersionEntity;
import com.pillapago.rules.domain.RuleSetVersionStatus;
import com.pillapago.rules.domain.RuleStatus;
import com.pillapago.rules.dto.ConditionDto;
import com.pillapago.rules.dto.RuleSnapshotItemDto;
import com.pillapago.rules.repository.RuleRepository;
import com.pillapago.rules.repository.RuleSetVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Servicio de caché en memoria de ultra-alta velocidad para las reglas activas de cada comercio (HU-08).
 * Garantiza activación atómica sin locks bloqueantes (O(1) pointer swap vía AtomicReference).
 * Una transacción concurrente jamás evalúa un conjunto parcial o a medias.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuleEngineCacheService {

    public record ActiveRuleSet(
            String versionName,
            int versionNumber,
            List<Rule> compiledRules
    ) {}

    private final RuleSetVersionRepository versionRepository;
    private final RuleRepository ruleRepository;
    private final ObjectMapper objectMapper;

    // Caché en memoria: merchantId -> AtomicReference<ActiveRuleSet>
    private final ConcurrentHashMap<UUID, AtomicReference<ActiveRuleSet>> cache = new ConcurrentHashMap<>();

    /**
     * Obtiene el conjunto de reglas activo del comercio desde la memoria (O(1)).
     * Si no está cargado, lo inicializa de forma thread-safe desde la base de datos.
     */
    public ActiveRuleSet getActiveRuleSet(UUID merchantId) {
        AtomicReference<ActiveRuleSet> ref = cache.computeIfAbsent(merchantId, this::loadFromDatabase);
        ActiveRuleSet current = ref.get();
        if (current != null && "v0-draft".equals(current.versionName())) {
            ref = loadFromDatabase(merchantId);
            cache.put(merchantId, ref);
            return ref.get();
        }
        return current;
    }

    /**
     * Realiza un swap atómico instantáneo del conjunto de reglas en memoria.
     * Cualquier hilo que esté evaluando leerá el snapshot anterior o el nuevo de forma consistente y atómica.
     */
    public void updateActiveRuleSet(UUID merchantId, ActiveRuleSet newRuleSet) {
        log.info("[RULE-ENGINE-CACHE] Actualizando atómicamente conjunto de reglas para comercio {} a versión [{}] con {} reglas",
                merchantId, newRuleSet.versionName(), newRuleSet.compiledRules().size());

        cache.compute(merchantId, (id, existingRef) -> {
            if (existingRef == null) {
                return new AtomicReference<>(newRuleSet);
            } else {
                existingRef.set(newRuleSet);
                return existingRef;
            }
        });
    }

    /**
     * Limpia la caché en memoria para pruebas o reinicios controlados.
     */
    public void clear(UUID merchantId) {
        if (merchantId != null) {
            cache.remove(merchantId);
        } else {
            cache.clear();
        }
    }

    private AtomicReference<ActiveRuleSet> loadFromDatabase(UUID merchantId) {
        log.info("[RULE-ENGINE-CACHE] Inicializando caché en memoria para comercio: {}", merchantId);

        // 1. Intentar cargar desde la última versión ACTIVE de rule_set_versions
        Optional<RuleSetVersionEntity> activeVersionOpt = versionRepository.findByMerchantIdAndStatus(merchantId, RuleSetVersionStatus.ACTIVE);

        if (activeVersionOpt.isPresent()) {
            RuleSetVersionEntity versionEntity = activeVersionOpt.get();
            List<Rule> compiled = compileFromSnapshot(versionEntity.getRulesSnapshot());
            ActiveRuleSet set = new ActiveRuleSet(versionEntity.getVersionName(), versionEntity.getVersionNumber(), compiled);
            return new AtomicReference<>(set);
        }

        // 2. Fallback de retrocompatibilidad: Si no hay versiones publicadas aún, compilar reglas activas directas
        List<RuleEntity> directRules = ruleRepository.findAllByMerchantIdWithConditions(merchantId).stream()
                .filter(r -> Boolean.TRUE.equals(r.getActive()) && (r.getStatus() == RuleStatus.PUBLISHED || r.getStatus() == RuleStatus.DRAFT))
                .toList();

        List<Rule> compiled = compileFromEntities(directRules);
        ActiveRuleSet defaultSet = new ActiveRuleSet("v0-draft", 0, compiled);
        return new AtomicReference<>(defaultSet);
    }

    public List<Rule> compileFromSnapshot(String snapshotJson) {
        try {
            List<RuleSnapshotItemDto> items = objectMapper.readValue(snapshotJson, new TypeReference<>() {});
            List<Rule> compiledRules = new ArrayList<>();

            for (RuleSnapshotItemDto item : items) {
                if (Boolean.FALSE.equals(item.getActive())) {
                    continue;
                }
                List<Condition> conditions = new ArrayList<>();
                if (item.getConditions() != null) {
                    for (ConditionDto cond : item.getConditions()) {
                        Operator op;
                        try {
                            op = Operator.valueOf(cond.getOperator());
                        } catch (IllegalArgumentException e) {
                            op = Operator.fromSymbol(cond.getOperator());
                        }
                        conditions.add(new Condition(cond.getField(), op, cond.getValue()));
                    }
                }
                if (!conditions.isEmpty()) {
                    compiledRules.add(new Rule(
                            item.getRuleId().toString(),
                            item.getName(),
                            item.getDescription() != null ? item.getDescription() : "",
                            conditions,
                            item.getPoints(),
                            Boolean.TRUE.equals(item.getActive())
                    ));
                }
            }

            return List.copyOf(compiledRules);
        } catch (Exception e) {
            log.error("[RULE-ENGINE-CACHE] Error al deserializar snapshot de reglas: {}", e.getMessage(), e);
            return List.of();
        }
    }

    public List<Rule> compileFromEntities(List<RuleEntity> entities) {
        List<Rule> rules = new ArrayList<>();
        for (RuleEntity entity : entities) {
            if (Boolean.FALSE.equals(entity.getActive())) {
                continue;
            }
            List<Condition> conditions = new ArrayList<>();
            for (RuleConditionEntity cond : entity.getConditions()) {
                Operator op;
                try {
                    op = Operator.valueOf(cond.getOperator());
                } catch (IllegalArgumentException e) {
                    op = Operator.fromSymbol(cond.getOperator());
                }
                conditions.add(new Condition(cond.getFieldName(), op, cond.getTargetValue()));
            }
            if (!conditions.isEmpty()) {
                rules.add(new Rule(
                        entity.getId().toString(),
                        entity.getName(),
                        entity.getDescription() != null ? entity.getDescription() : "",
                        conditions,
                        entity.getPoints(),
                        Boolean.TRUE.equals(entity.getActive())
                ));
            }
        }
        return List.copyOf(rules);
    }
}
