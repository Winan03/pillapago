package com.pillapago.evaluation.repository;

import com.pillapago.evaluation.domain.CaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CaseRepository extends JpaRepository<CaseEntity, UUID> {

    List<CaseEntity> findAllByMerchantId(UUID merchantId);

    List<CaseEntity> findAllByMerchantIdAndStatus(UUID merchantId, String status);

    Optional<CaseEntity> findByTransactionId(UUID transactionId);
}
