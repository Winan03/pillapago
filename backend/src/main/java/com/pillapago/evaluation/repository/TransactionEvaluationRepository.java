package com.pillapago.evaluation.repository;

import com.pillapago.evaluation.domain.TransactionEvaluationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TransactionEvaluationRepository extends JpaRepository<TransactionEvaluationEntity, UUID> {

    List<TransactionEvaluationEntity> findAllByTransactionId(UUID transactionId);
}
