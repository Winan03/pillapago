package com.pillapago.tenant.repository;

import com.pillapago.tenant.domain.TenantKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantKeyRepository extends JpaRepository<TenantKey, UUID> {

    Optional<TenantKey> findByMerchantIdAndActiveTrue(UUID merchantId);

    Optional<TenantKey> findByMerchantIdAndKeyVersion(UUID merchantId, Integer keyVersion);

    void deleteByMerchantId(UUID merchantId);
}
