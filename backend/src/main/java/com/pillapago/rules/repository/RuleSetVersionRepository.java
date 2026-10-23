package com.pillapago.rules.repository;

import com.pillapago.rules.domain.RuleSetVersionEntity;
import com.pillapago.rules.domain.RuleSetVersionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RuleSetVersionRepository extends JpaRepository<RuleSetVersionEntity, UUID> {

    Optional<RuleSetVersionEntity> findByMerchantIdAndStatus(UUID merchantId, RuleSetVersionStatus status);

    Optional<RuleSetVersionEntity> findByMerchantIdAndVersionName(UUID merchantId, String versionName);

    @Query("SELECT MAX(r.versionNumber) FROM RuleSetVersionEntity r WHERE r.merchant.id = :merchantId")
    Integer findMaxVersionNumberByMerchantId(@Param("merchantId") UUID merchantId);

    List<RuleSetVersionEntity> findAllByMerchantIdOrderByVersionNumberDesc(UUID merchantId);

    @Modifying
    @Query("UPDATE RuleSetVersionEntity r SET r.status = :newStatus WHERE r.merchant.id = :merchantId AND r.status = :currentStatus")
    int updateStatusByMerchantIdAndStatus(
            @Param("merchantId") UUID merchantId,
            @Param("currentStatus") RuleSetVersionStatus currentStatus,
            @Param("newStatus") RuleSetVersionStatus newStatus
    );
}
