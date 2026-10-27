package com.pillapago.lists.service;

import com.pillapago.lists.domain.ListEntryEntity;
import com.pillapago.lists.domain.ListEntryType;
import com.pillapago.lists.domain.ListType;
import com.pillapago.lists.dto.CreateListEntryRequest;
import com.pillapago.lists.dto.ListEntryResponseDto;
import com.pillapago.lists.exception.ListEntryAlreadyExistsException;
import com.pillapago.lists.repository.ListEntryRepository;
import com.pillapago.shared.crypto.FieldEncryptor;
import com.pillapago.shared.crypto.HmacIndexService;
import com.pillapago.shared.exception.ResourceNotFoundException;
import com.pillapago.tenant.domain.Merchant;
import com.pillapago.tenant.domain.User;
import com.pillapago.tenant.repository.MerchantRepository;
import com.pillapago.tenant.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Servicio de negocio para la administración de listas de bloqueo y confianza (HU-13 / RF-09).
 * Maneja cifrado de campo con Tink, índices ciegos con HMAC y sincronización inmediata con la caché en memoria.
 */
@Service
@RequiredArgsConstructor
public class ListService {

    private static final Logger log = LoggerFactory.getLogger(ListService.class);
    private static final Logger auditLog = LoggerFactory.getLogger("AUDIT_LOGGER");

    private final ListEntryRepository listEntryRepository;
    private final MerchantRepository merchantRepository;
    private final UserRepository userRepository;
    private final HmacIndexService hmacIndexService;
    private final FieldEncryptor fieldEncryptor;
    private final ListCacheService listCacheService;

    /**
     * Registra una nueva entrada en la lista de bloqueo o confianza.
     * Cifra el valor con Tink, genera índice ciego HMAC y actualiza la caché en memoria en caliente.
     */
    @Transactional
    public ListEntryResponseDto createEntry(UUID merchantId, UUID analystId, CreateListEntryRequest request) {
        String rawValue = request.getValue().trim();
        log.info("Creando entrada de lista [{}] para comercio {} por analista {}", request.getListType(), merchantId, analystId);

        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Comercio no encontrado: " + merchantId));

        User analyst = userRepository.findById(analystId)
                .orElseThrow(() -> new ResourceNotFoundException("Analista no encontrado: " + analystId));

        // 1. Cálculo del índice ciego determinista (HMAC-SHA256 con clave del comercio)
        String hashedValue = hmacIndexService.computeBlindIndex(merchantId, rawValue);

        // 2. Validación de duplicidad en la misma lista y comercio (409 Conflict)
        listEntryRepository.findByMerchantIdAndListTypeAndEntryTypeAndHashedValue(
                merchantId, request.getListType(), request.getEntryType(), hashedValue
        ).ifPresent(existing -> {
            throw new ListEntryAlreadyExistsException(
                    "El elemento ya se encuentra registrado en la lista " + request.getListType() +
                    " para el tipo " + request.getEntryType()
            );
        });

        // 3. Generación de máscara visual segura (RNF-15)
        String maskedValue = maskValue(request.getEntryType(), rawValue);

        // 4. Cifrado autenticado con Tink AES-256-GCM
        UUID entryId = UUID.randomUUID();
        String encryptedValue = fieldEncryptor.encryptString(merchantId, rawValue, "list_entries", "encrypted_value", entryId);

        // 5. Persistencia en PostgreSQL
        ListEntryEntity entity = ListEntryEntity.builder()
                .id(entryId)
                .merchant(merchant)
                .listType(request.getListType())
                .entryType(request.getEntryType())
                .hashedValue(hashedValue)
                .encryptedValue(encryptedValue)
                .valueMasked(maskedValue)
                .reason(request.getReason().trim())
                .expiresAt(request.getExpiresAt())
                .createdBy(analyst)
                .build();

        ListEntryEntity saved = listEntryRepository.save(entity);

        // 6. Actualización en caliente de la caché en memoria (Criterio 2)
        listCacheService.refreshMerchantLists(merchantId);

        // 7. Registro de auditoría (RF-20 / preparación HU-19)
        auditLog.info("[AUDIT-HU13] ALTA_LISTA | Comercio: {} | Usuario: {} ({}) | TipoLista: {} | TipoEntrada: {} | IdEntrada: {} | Motivo: '{}' | Expira: {}",
                merchantId, analyst.getUsername(), analyst.getId(), saved.getListType(), saved.getEntryType(),
                saved.getId(), saved.getReason(), saved.getExpiresAt());

        return mapToDto(saved);
    }

    /**
     * Consulta las entradas de lista del comercio aplicando filtros opcionales de lista y tipo.
     */
    @Transactional(readOnly = true)
    public List<ListEntryResponseDto> getEntries(UUID merchantId, ListType listType, ListEntryType entryType) {
        log.debug("Consultando entradas de lista para comercio {} con filtro listType={}, entryType={}", merchantId, listType, entryType);

        List<ListEntryEntity> entities;
        if (listType != null) {
            entities = listEntryRepository.findAllByMerchantIdAndListTypeOrderByCreatedAtDesc(merchantId, listType);
        } else {
            entities = listEntryRepository.findAllByMerchantIdOrderByCreatedAtDesc(merchantId);
        }

        if (entryType != null) {
            entities = entities.stream()
                    .filter(e -> e.getEntryType() == entryType)
                    .collect(Collectors.toList());
        }

        return entities.stream().map(this::mapToDto).collect(Collectors.toList());
    }

    /**
     * Obtiene el detalle de una entrada específica del comercio.
     */
    @Transactional(readOnly = true)
    public ListEntryResponseDto getEntry(UUID merchantId, UUID entryId) {
        ListEntryEntity entity = listEntryRepository.findByIdAndMerchantId(entryId, merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Entrada de lista no encontrada con id: " + entryId));
        return mapToDto(entity);
    }

    /**
     * Elimina una entrada de lista y actualiza inmediatamente la memoria RAM para que no aplique en la siguiente evaluación.
     */
    @Transactional
    public void deleteEntry(UUID merchantId, UUID entryId, UUID analystId) {
        log.info("Eliminando entrada de lista {} para comercio {} por analista {}", entryId, merchantId, analystId);

        ListEntryEntity entity = listEntryRepository.findByIdAndMerchantId(entryId, merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Entrada de lista no encontrada con id: " + entryId));

        User analyst = userRepository.findById(analystId)
                .orElseThrow(() -> new ResourceNotFoundException("Analista no encontrado: " + analystId));

        listEntryRepository.delete(entity);

        // Actualización inmediata de la caché en memoria (Criterio 2)
        listCacheService.refreshMerchantLists(merchantId);

        // Registro de auditoría (RF-20 / preparación HU-19)
        auditLog.info("[AUDIT-HU13] BAJA_LISTA | Comercio: {} | Usuario: {} ({}) | TipoLista: {} | TipoEntrada: {} | IdEntrada: {} | MotivoOriginal: '{}'",
                merchantId, analyst.getUsername(), analyst.getId(), entity.getListType(), entity.getEntryType(),
                entity.getId(), entity.getReason());
    }

    private ListEntryResponseDto mapToDto(ListEntryEntity entity) {
        return ListEntryResponseDto.builder()
                .id(entity.getId())
                .listType(entity.getListType())
                .entryType(entity.getEntryType())
                .valueMasked(entity.getValueMasked())
                .reason(entity.getReason())
                .expiresAt(entity.getExpiresAt())
                .active(entity.isActive())
                .createdById(entity.getCreatedBy() != null ? entity.getCreatedBy().getId() : null)
                .createdByName(entity.getCreatedBy() != null ? entity.getCreatedBy().getUsername() : null)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    /**
     * Genera una máscara visual segura para mostrar en la interfaz sin exponer el dato sensible completo (RNF-15).
     */
    private String maskValue(ListEntryType type, String raw) {
        if (raw == null || raw.isBlank()) {
            return "***";
        }
        return switch (type) {
            case EMAIL -> {
                int atIdx = raw.indexOf('@');
                if (atIdx <= 1) {
                    yield "***" + raw.substring(Math.max(0, atIdx));
                }
                String name = raw.substring(0, atIdx);
                String domain = raw.substring(atIdx);
                yield name.charAt(0) + "***" + domain;
            }
            case IP -> {
                String[] parts = raw.split("\\.");
                if (parts.length == 4) {
                    yield parts[0] + "." + parts[1] + ".***.***";
                }
                yield raw.length() > 6 ? raw.substring(0, 4) + "***" : "***";
            }
            case CARD_FINGERPRINT -> {
                if (raw.length() >= 8) {
                    yield raw.substring(0, 4) + "..." + raw.substring(raw.length() - 4);
                }
                yield "****" + (raw.length() > 4 ? raw.substring(raw.length() - 4) : raw);
            }
            case DEVICE -> {
                if (raw.length() > 8) {
                    yield raw.substring(0, 4) + "***" + raw.substring(raw.length() - 4);
                }
                yield "dev_" + (raw.length() > 4 ? raw.substring(raw.length() - 4) : "***");
            }
        };
    }
}
