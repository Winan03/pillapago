package com.pillapago.lists.service;

import com.pillapago.lists.domain.ListEntryEntity;
import com.pillapago.lists.domain.ListEntryType;
import com.pillapago.lists.domain.ListType;
import com.pillapago.lists.repository.ListEntryRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Servicio de caché en memoria de ultra-alta velocidad O(1) para listas de bloqueo y confianza (HU-13 / RNF-01).
 * Almacena los índices ciegos (HMAC-SHA256) de entradas activas por comercio en memoria RAM.
 * Permite que el Hot Path de evaluación realice el descarte de fraudes conocidos sin consultar PostgreSQL.
 */
@Service
@RequiredArgsConstructor
public class ListCacheService {

    private static final Logger log = LoggerFactory.getLogger(ListCacheService.class);

    public record CachedListEntry(
            UUID id,
            ListType listType,
            ListEntryType entryType,
            String hashedValue,
            String reason,
            OffsetDateTime expiresAt
    ) {
        public boolean isExpired(OffsetDateTime now) {
            return expiresAt != null && expiresAt.isBefore(now);
        }
    }

    public record MerchantActiveLists(
            Map<ListEntryType, Map<String, CachedListEntry>> blockMap,
            Map<ListEntryType, Map<String, CachedListEntry>> allowMap
    ) {}

    public record ListCheckResult(
            boolean matched,
            ListType listType,
            ListEntryType entryType,
            String reason
    ) {
        public static ListCheckResult noMatch() {
            return new ListCheckResult(false, null, null, null);
        }

        public static ListCheckResult match(ListType listType, ListEntryType entryType, String reason) {
            return new ListCheckResult(true, listType, entryType, reason);
        }
    }

    private final ListEntryRepository listEntryRepository;

    // Caché en memoria por comercio: merchantId -> AtomicReference<MerchantActiveLists>
    private final ConcurrentHashMap<UUID, AtomicReference<MerchantActiveLists>> memoryCache = new ConcurrentHashMap<>();

    /**
     * Evalúa los identificadores hasheados de una transacción entrante contra las listas en memoria.
     * Prioridad 1: Coincidencia en BLOCK -> REJECT inmediato.
     * Prioridad 2: Coincidencia en ALLOW -> APPROVE inmediato (Whitelist).
     * Si no hay coincidencias o las entradas expiraron, retorna noMatch().
     */
    public ListCheckResult evaluateTransaction(UUID merchantId, Map<ListEntryType, String> hashedIdentifiers) {
        if (merchantId == null || hashedIdentifiers == null || hashedIdentifiers.isEmpty()) {
            return ListCheckResult.noMatch();
        }

        MerchantActiveLists activeLists = getOrLoadLists(merchantId);
        OffsetDateTime now = OffsetDateTime.now();

        // 1. Verificación prioritaria de BLOCKLIST (Criterio 3: Coincidencia en BLOCK devuelve REJECT)
        for (Map.Entry<ListEntryType, String> entry : hashedIdentifiers.entrySet()) {
            ListEntryType type = entry.getKey();
            String hash = entry.getValue();
            if (hash == null || hash.isBlank()) {
                continue;
            }

            Map<String, CachedListEntry> blockByType = activeLists.blockMap().get(type);
            if (blockByType != null) {
                CachedListEntry matched = blockByType.get(hash);
                if (matched != null && !matched.isExpired(now)) {
                    log.info("[HOT-PATH-LIST] Coincidencia en BLOCKLIST para comercio {}: tipo={}, hash={}", merchantId, type, hash);
                    return ListCheckResult.match(ListType.BLOCK, type, matched.reason());
                }
            }
        }

        // 2. Verificación secundaria de ALLOWLIST (Lista blanca de confianza)
        for (Map.Entry<ListEntryType, String> entry : hashedIdentifiers.entrySet()) {
            ListEntryType type = entry.getKey();
            String hash = entry.getValue();
            if (hash == null || hash.isBlank()) {
                continue;
            }

            Map<String, CachedListEntry> allowByType = activeLists.allowMap().get(type);
            if (allowByType != null) {
                CachedListEntry matched = allowByType.get(hash);
                if (matched != null && !matched.isExpired(now)) {
                    log.info("[HOT-PATH-LIST] Coincidencia en ALLOWLIST para comercio {}: tipo={}, hash={}", merchantId, type, hash);
                    return ListCheckResult.match(ListType.ALLOW, type, matched.reason());
                }
            }
        }

        return ListCheckResult.noMatch();
    }

    /**
     * Refresca atómicamente la memoria del comercio tras un alta o baja en la API (Criterio 2: se aplica en la siguiente evaluación).
     */
    public void refreshMerchantLists(UUID merchantId) {
        log.info("[LIST-CACHE] Refrescando listas en memoria para comercio: {}", merchantId);
        MerchantActiveLists updated = loadFromDatabase(merchantId);
        memoryCache.compute(merchantId, (id, ref) -> {
            if (ref == null) {
                return new AtomicReference<>(updated);
            }
            ref.set(updated);
            return ref;
        });
    }

    /**
     * Limpia la caché en memoria de un comercio (útil en tests o reset).
     */
    public void invalidate(UUID merchantId) {
        memoryCache.remove(merchantId);
    }

    /**
     * Limpia completamente la caché.
     */
    public void clearAll() {
        memoryCache.clear();
    }

    private MerchantActiveLists getOrLoadLists(UUID merchantId) {
        AtomicReference<MerchantActiveLists> ref = memoryCache.computeIfAbsent(
                merchantId,
                id -> new AtomicReference<>(loadFromDatabase(id))
        );
        return ref.get();
    }

    private MerchantActiveLists loadFromDatabase(UUID merchantId) {
        OffsetDateTime now = OffsetDateTime.now();
        List<ListEntryEntity> entities = listEntryRepository.findAllActiveByMerchantId(merchantId, now);

        Map<ListEntryType, Map<String, CachedListEntry>> blockMap = new EnumMap<>(ListEntryType.class);
        Map<ListEntryType, Map<String, CachedListEntry>> allowMap = new EnumMap<>(ListEntryType.class);

        for (ListEntryType type : ListEntryType.values()) {
            blockMap.put(type, new HashMap<>());
            allowMap.put(type, new HashMap<>());
        }

        for (ListEntryEntity entity : entities) {
            CachedListEntry cached = new CachedListEntry(
                    entity.getId(),
                    entity.getListType(),
                    entity.getEntryType(),
                    entity.getHashedValue(),
                    entity.getReason(),
                    entity.getExpiresAt()
            );

            if (entity.getListType() == ListType.BLOCK) {
                blockMap.get(entity.getEntryType()).put(entity.getHashedValue(), cached);
            } else if (entity.getListType() == ListType.ALLOW) {
                allowMap.get(entity.getEntryType()).put(entity.getHashedValue(), cached);
            }
        }

        log.debug("[LIST-CACHE] Cargadas {} entradas vigentes para comercio {}", entities.size(), merchantId);
        return new MerchantActiveLists(blockMap, allowMap);
    }
}
