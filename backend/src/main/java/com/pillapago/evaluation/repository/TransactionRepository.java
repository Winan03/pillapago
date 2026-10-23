package com.pillapago.evaluation.repository;

import com.pillapago.evaluation.domain.TransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<TransactionEntity, UUID> {

    Optional<TransactionEntity> findByMerchantIdAndExternalTransactionId(UUID merchantId, String externalTransactionId);

    boolean existsByMerchantIdAndExternalTransactionId(UUID merchantId, String externalTransactionId);
}
