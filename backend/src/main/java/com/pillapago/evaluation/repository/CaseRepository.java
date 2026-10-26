package com.pillapago.evaluation.repository;

import com.pillapago.evaluation.domain.CaseEntity;
import com.pillapago.evaluation.domain.CaseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio JPA para la entidad de Casos de revisión (HU-09).
 * Provee consultas con aislamiento estricto por comercio y filtros de antigüedad/monto.
 */
@Repository
public interface CaseRepository extends JpaRepository<CaseEntity, UUID> {

    List<CaseEntity> findAllByMerchantId(UUID merchantId);

    List<CaseEntity> findAllByMerchantIdAndStatus(UUID merchantId, CaseStatus status);

    Optional<CaseEntity> findByIdAndMerchantId(UUID id, UUID merchantId);

    Optional<CaseEntity> findByTransactionId(UUID transactionId);

    /**
     * Consulta con filtros de cola de casos para analistas de riesgo (HU-09).
     * Solo retorna registros del comercio indicado, filtrando opcionalmente por estado,
     * antigüedad (creados a partir de cierta fecha) y rango de montos de la transacción.
     */
    @Query("""
        SELECT c FROM CaseEntity c
        JOIN c.transaction t
        WHERE c.merchant.id = :merchantId
          AND (:status IS NULL OR c.status = :status)
          AND (:sinceDate IS NULL OR c.createdAt >= :sinceDate)
          AND (:minAmount IS NULL OR t.amount >= :minAmount)
          AND (:maxAmount IS NULL OR t.amount <= :maxAmount)
        ORDER BY c.createdAt DESC
    """)
    List<CaseEntity> searchCases(
            @Param("merchantId") UUID merchantId,
            @Param("status") CaseStatus status,
            @Param("sinceDate") OffsetDateTime sinceDate,
            @Param("minAmount") BigDecimal minAmount,
            @Param("maxAmount") BigDecimal maxAmount
    );
}
