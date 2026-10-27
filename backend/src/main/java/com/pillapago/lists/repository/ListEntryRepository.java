package com.pillapago.lists.repository;

import com.pillapago.lists.domain.ListEntryEntity;
import com.pillapago.lists.domain.ListEntryType;
import com.pillapago.lists.domain.ListType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio JPA para las listas de bloqueo y confianza (HU-13).
 * Aislamiento estricto por comercio (`merchant_id`).
 */
@Repository
public interface ListEntryRepository extends JpaRepository<ListEntryEntity, UUID> {

    Optional<ListEntryEntity> findByIdAndMerchantId(UUID id, UUID merchantId);

    List<ListEntryEntity> findAllByMerchantIdOrderByCreatedAtDesc(UUID merchantId);

    List<ListEntryEntity> findAllByMerchantIdAndListTypeOrderByCreatedAtDesc(UUID merchantId, ListType listType);

    Optional<ListEntryEntity> findByMerchantIdAndListTypeAndEntryTypeAndHashedValue(
            UUID merchantId,
            ListType listType,
            ListEntryType entryType,
            String hashedValue
    );

    /**
     * Obtiene todas las entradas vigentes del comercio para carga en caché en memoria (O(1)).
     */
    @Query("""
        SELECT e FROM ListEntryEntity e
        WHERE e.merchant.id = :merchantId
          AND (e.expiresAt IS NULL OR e.expiresAt > :now)
    """)
    List<ListEntryEntity> findAllActiveByMerchantId(
            @Param("merchantId") UUID merchantId,
            @Param("now") OffsetDateTime now
    );
}
