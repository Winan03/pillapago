package com.pillapago.evaluation.service;

import com.pillapago.evaluation.domain.CaseEntity;
import com.pillapago.evaluation.domain.CaseResolution;
import com.pillapago.evaluation.domain.CaseStatus;
import com.pillapago.evaluation.domain.TransactionEvaluationEntity;
import com.pillapago.evaluation.dto.*;
import com.pillapago.evaluation.exception.CaseAlreadyResolvedException;
import com.pillapago.evaluation.repository.CaseRepository;
import com.pillapago.shared.exception.ResourceNotFoundException;
import com.pillapago.tenant.domain.User;
import com.pillapago.tenant.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Servicio de negocio para la gestión y resolución de casos de revisión de fraude (HU-09 / RF-14 / RF-15).
 * Implementa aislamiento estricto multi-tenant y control de concurrencia optimista con JPA @Version.
 */
@Service
@RequiredArgsConstructor
public class CaseService {

    private static final Logger log = LoggerFactory.getLogger(CaseService.class);

    private final CaseRepository caseRepository;
    private final UserRepository userRepository;

    /**
     * Lista los casos del comercio aplicando filtros opcionales de antigüedad, monto y estado.
     * Solo retorna información perteneciente al comercio autenticado (Aislamiento Tenant).
     */
    @Transactional(readOnly = true)
    public List<CaseSummaryDto> getCases(UUID merchantId, CaseFilterRequest filter) {
        log.debug("Consultando casos para comercio: {} con filtros: {}", merchantId, filter);

        OffsetDateTime sinceDate = null;
        if (filter != null && filter.getAgeDays() != null && filter.getAgeDays() > 0) {
            sinceDate = OffsetDateTime.now().minusDays(filter.getAgeDays());
        }

        CaseStatus status = (filter != null) ? filter.getStatus() : null;
        var minAmount = (filter != null) ? filter.getMinAmount() : null;
        var maxAmount = (filter != null) ? filter.getMaxAmount() : null;

        List<CaseEntity> entities = caseRepository.searchCases(merchantId, status, sinceDate, minAmount, maxAmount);
        return entities.stream().map(this::mapToSummary).collect(Collectors.toList());
    }

    /**
     * Obtiene el detalle exhaustivo de un caso, incluyendo reglas activadas y versión de reglas.
     */
    @Transactional(readOnly = true)
    public CaseDetailDto getCaseDetail(UUID merchantId, UUID caseId) {
        CaseEntity caseEntity = findCaseOrThrow(merchantId, caseId);
        return mapToDetail(caseEntity);
    }

    /**
     * Reclama y asigna un caso al analista autenticado (transición a ASSIGNED).
     * Si el caso ya está resuelto, se rechaza la operación para preservar inmutabilidad.
     */
    @Transactional
    public CaseSummaryDto claimCase(UUID merchantId, UUID caseId, UUID analystId) {
        log.info("Analista {} reclamando caso {} del comercio {}", analystId, caseId, merchantId);

        CaseEntity caseEntity = findCaseOrThrow(merchantId, caseId);

        if (caseEntity.getStatus() == CaseStatus.RESOLVED) {
            throw new CaseAlreadyResolvedException("No se puede reclamar el caso " + caseId + " porque ya fue resuelto.");
        }

        User analyst = userRepository.findById(analystId)
                .orElseThrow(() -> new ResourceNotFoundException("Analista de riesgo no encontrado con id: " + analystId));

        caseEntity.setAssignedTo(analyst);
        caseEntity.setStatus(CaseStatus.ASSIGNED);

        // Guardado con bloqueo optimista automático (incrementa version)
        CaseEntity saved = caseRepository.save(caseEntity);
        log.info("Caso {} asignado exitosamente a {}", caseId, analyst.getUsername());
        return mapToSummary(saved);
    }

    /**
     * Resuelve un caso como APROBADO o RECHAZADO, exigiendo justificación obligatoria.
     * Un caso resuelto queda completamente inmutable ante futuras peticiones.
     * En caso de concurrencia simultánea, JPA lanzará OptimisticLockingFailureException si
     * otro analista modificó la entidad previamente.
     */
    @Transactional
    public CaseSummaryDto resolveCase(UUID merchantId, UUID caseId, UUID analystId, ResolveCaseRequest request) {
        log.info("Analista {} resolviendo caso {} con resolución: {}", analystId, caseId, request.getResolution());

        CaseEntity caseEntity = findCaseOrThrow(merchantId, caseId);

        // Validación de inmutabilidad
        if (caseEntity.getStatus() == CaseStatus.RESOLVED) {
            throw new CaseAlreadyResolvedException("El caso " + caseId + " ya fue resuelto previamente y no puede ser modificado.");
        }

        User analyst = userRepository.findById(analystId)
                .orElseThrow(() -> new ResourceNotFoundException("Analista de riesgo no encontrado con id: " + analystId));

        caseEntity.setStatus(CaseStatus.RESOLVED);
        caseEntity.setResolution(request.getResolution());
        caseEntity.setResolutionNotes(request.getComment().trim());
        caseEntity.setResolvedBy(analyst);
        caseEntity.setResolvedAt(OffsetDateTime.now());

        // Si no estaba asignado o estaba asignado a otro, se actualiza el analista asignado
        if (caseEntity.getAssignedTo() == null) {
            caseEntity.setAssignedTo(analyst);
        }

        CaseEntity saved = caseRepository.save(caseEntity);
        log.info("Caso {} resuelto exitosamente como {} por {}", caseId, saved.getResolution(), analyst.getUsername());
        return mapToSummary(saved);
    }

    private CaseEntity findCaseOrThrow(UUID merchantId, UUID caseId) {
        return caseRepository.findByIdAndMerchantId(caseId, merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Caso de revisión no encontrado: " + caseId));
    }

    private CaseSummaryDto mapToSummary(CaseEntity entity) {
        var tx = entity.getTransaction();
        return CaseSummaryDto.builder()
                .id(entity.getId())
                .version(entity.getVersion())
                .status(entity.getStatus())
                .priority(entity.getPriority())
                .transactionId(tx != null ? tx.getId() : null)
                .externalTransactionId(tx != null ? tx.getExternalTransactionId() : null)
                .amount(tx != null ? tx.getAmount() : null)
                .currency(tx != null ? tx.getCurrency() : null)
                .riskScore(tx != null ? tx.getRiskScore() : null)
                .ruleSetVersion(tx != null ? tx.getRuleSetVersion() : null)
                .paymentMethod(tx != null ? tx.getPaymentMethod() : null)
                .cardBin(tx != null ? tx.getCardBin() : null)
                .cardLastFour(tx != null ? tx.getCardLastFour() : null)
                .assignedToId(entity.getAssignedTo() != null ? entity.getAssignedTo().getId() : null)
                .assignedToName(entity.getAssignedTo() != null ? entity.getAssignedTo().getUsername() : null)
                .resolution(entity.getResolution())
                .resolvedById(entity.getResolvedBy() != null ? entity.getResolvedBy().getId() : null)
                .resolvedByName(entity.getResolvedBy() != null ? entity.getResolvedBy().getUsername() : null)
                .resolvedAt(entity.getResolvedAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private CaseDetailDto mapToDetail(CaseEntity entity) {
        var tx = entity.getTransaction();

        List<RuleReasonDto> reasons = List.of();
        if (tx != null && tx.getEvaluations() != null) {
            reasons = tx.getEvaluations().stream()
                    .map(eval -> RuleReasonDto.builder()
                            .ruleName(eval.getRuleName())
                            .points(eval.getPoints())
                            .description(eval.getDescription())
                            .build())
                    .collect(Collectors.toList());
        }

        return CaseDetailDto.builder()
                .id(entity.getId())
                .version(entity.getVersion())
                .status(entity.getStatus())
                .priority(entity.getPriority())
                .notes(entity.getNotes())
                .transactionId(tx != null ? tx.getId() : null)
                .externalTransactionId(tx != null ? tx.getExternalTransactionId() : null)
                .amount(tx != null ? tx.getAmount() : null)
                .currency(tx != null ? tx.getCurrency() : null)
                .riskScore(tx != null ? tx.getRiskScore() : null)
                .ruleSetVersion(tx != null ? tx.getRuleSetVersion() : null)
                .degraded(tx != null ? tx.getDegraded() : null)
                .latencyMs(tx != null ? tx.getLatencyMs() : null)
                .paymentMethod(tx != null ? tx.getPaymentMethod() : null)
                .cardBin(tx != null ? tx.getCardBin() : null)
                .cardLastFour(tx != null ? tx.getCardLastFour() : null)
                .metadata(tx != null ? tx.getMetadata() : null)
                .transactionCreatedAt(tx != null ? tx.getCreatedAt() : null)
                .triggeredRules(reasons)
                .assignedToId(entity.getAssignedTo() != null ? entity.getAssignedTo().getId() : null)
                .assignedToName(entity.getAssignedTo() != null ? entity.getAssignedTo().getUsername() : null)
                .assignedToEmail(entity.getAssignedTo() != null ? entity.getAssignedTo().getEmail() : null)
                .resolution(entity.getResolution())
                .resolutionNotes(entity.getResolutionNotes())
                .resolvedById(entity.getResolvedBy() != null ? entity.getResolvedBy().getId() : null)
                .resolvedByName(entity.getResolvedBy() != null ? entity.getResolvedBy().getUsername() : null)
                .resolvedByEmail(entity.getResolvedBy() != null ? entity.getResolvedBy().getEmail() : null)
                .resolvedAt(entity.getResolvedAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
