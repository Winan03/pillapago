package com.pillapago.rules.repository;

import com.pillapago.rules.domain.RuleEntity;
import com.pillapago.rules.domain.RuleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RuleRepository extends JpaRepository<RuleEntity, UUID> {

    @Query("SELECT DISTINCT r FROM RuleEntity r LEFT JOIN FETCH r.conditions WHERE r.merchant.id = :merchantId ORDER BY r.createdAt DESC")
    List<RuleEntity> findAllByMerchantIdWithConditions(@Param("merchantId") UUID merchantId);

    @Query("SELECT DISTINCT r FROM RuleEntity r LEFT JOIN FETCH r.conditions WHERE r.merchant.id = :merchantId AND r.status = :status ORDER BY r.createdAt DESC")
    List<RuleEntity> findAllByMerchantIdAndStatusWithConditions(@Param("merchantId") UUID merchantId, @Param("status") RuleStatus status);

    @Query("SELECT r FROM RuleEntity r LEFT JOIN FETCH r.conditions WHERE r.id = :id AND r.merchant.id = :merchantId")
    Optional<RuleEntity> findByIdAndMerchantIdWithConditions(@Param("id") UUID id, @Param("merchantId") UUID merchantId);

    boolean existsByMerchantIdAndNameAndStatus(UUID merchantId, String name, RuleStatus status);

    boolean existsByMerchantIdAndNameAndStatusAndIdNot(UUID merchantId, String name, RuleStatus status, UUID id);
}
