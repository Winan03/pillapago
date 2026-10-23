package com.pillapago.tenant.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.pillapago.shared.crypto.ApiKeyCryptoService;
import com.pillapago.shared.exception.ResourceNotFoundException;
import com.pillapago.tenant.domain.ApiKey;
import com.pillapago.tenant.domain.Merchant;
import com.pillapago.tenant.dto.ApiKeyCreatedResponse;
import com.pillapago.tenant.dto.ApiKeyRevokedResponse;
import com.pillapago.tenant.dto.ApiKeySummaryResponse;
import com.pillapago.tenant.dto.CreateApiKeyRequest;
import com.pillapago.tenant.repository.ApiKeyRepository;
import com.pillapago.tenant.repository.MerchantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Servicio de lógica de negocio para la gestión segura del ciclo de vida de API Keys (HU-05 y HU-06c).
 * Optimizado para el Hot Path mediante una caché en memoria Caffeine (15 min TTL) con invalidación inmediata al revocar.
 */
@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;
    private final MerchantRepository merchantRepository;
    private final ApiKeyCryptoService cryptoService;

    // Caché en memoria para evitar consultas recurrentes a PostgreSQL en el Hot Path de transacciones
    private final Cache<String, ApiKey> apiKeyCache = Caffeine.newBuilder()
            .maximumSize(5000)
            .expireAfterWrite(15, TimeUnit.MINUTES)
            .build();

    /**
     * Genera una nueva API Key para el comercio.
     * Retorna la clave en texto plano UNA ÚNICA VEZ en la respuesta y guarda únicamente el hash SHA-256 en BD.
     */
    @Transactional
    public ApiKeyCreatedResponse createApiKey(CreateApiKeyRequest request, UUID merchantId) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Comercio no encontrado con ID: " + merchantId,
                        "El comercio asociado al usuario autenticado no existe."
                ));

        String rawKey = cryptoService.generateRawKey();
        String keyPrefix = cryptoService.extractPrefix(rawKey);
        String keyHash = cryptoService.computeSha256(rawKey);

        ApiKey apiKey = ApiKey.builder()
                .merchant(merchant)
                .name(request.getName().trim())
                .keyPrefix(keyPrefix)
                .keyHash(keyHash)
                .active(true)
                .build();

        ApiKey saved = apiKeyRepository.save(apiKey);

        // Opcional: pre-cachear la nueva clave activa
        apiKeyCache.put(keyHash, saved);

        return ApiKeyCreatedResponse.builder()
                .id(saved.getId())
                .name(saved.getName())
                .keyPrefix(saved.getKeyPrefix())
                .apiKey(rawKey) // Solo devuelto en esta respuesta de creación
                .active(saved.getActive())
                .createdAt(saved.getCreatedAt())
                .userMessage("¡Clave generada con éxito! Cópiala y guárdala ahora en un lugar seguro. Por motivos de seguridad, no se volverá a mostrar nunca más.")
                .build();
    }

    /**
     * Lista de forma segura las API Keys del comercio.
     * Oculta rigurosamente el texto plano y el hash, mostrando únicamente el prefijo identificador.
     */
    @Transactional(readOnly = true)
    public List<ApiKeySummaryResponse> listApiKeys(UUID merchantId) {
        return apiKeyRepository.findAllByMerchantIdOrderByCreatedAtDesc(merchantId).stream()
                .map(key -> ApiKeySummaryResponse.builder()
                        .id(key.getId())
                        .name(key.getName())
                        .keyPrefix(key.getKeyPrefix())
                        .active(key.getActive())
                        .createdAt(key.getCreatedAt())
                        .lastUsedAt(key.getLastUsedAt())
                        .build())
                .toList();
    }

    /**
     * Revoca inmediatamente la API Key especificada.
     * Invalida proactivamente la entrada en la caché en memoria para garantizar cese inmediato de acceso.
     */
    @Transactional
    public ApiKeyRevokedResponse revokeApiKey(UUID apiKeyId, UUID merchantId) {
        ApiKey apiKey = apiKeyRepository.findByIdAndMerchantId(apiKeyId, merchantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "La API key solicitada no existe o no pertenece a su comercio.",
                        "No se encontró la API key especificada en tu cuenta de comercio."
                ));

        apiKey.setActive(false);
        apiKeyRepository.save(apiKey);

        // Invalidar caché en memoria de inmediato
        apiKeyCache.invalidate(apiKey.getKeyHash());

        return ApiKeyRevokedResponse.builder()
                .id(apiKey.getId())
                .status("REVOKED")
                .userMessage(String.format("La API key '%s' ha sido revocada exitosamente y ha dejado de funcionar de inmediato.", apiKey.getName()))
                .build();
    }

    /**
     * Valida una API key a partir de su texto plano entrante.
     * Optimizado mediante la caché en memoria Caffeine para responder en microsegundos en el Hot Path.
     */
    @Transactional(readOnly = true)
    public Optional<ApiKey> findByRawKey(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return Optional.empty();
        }
        String keyHash = cryptoService.computeSha256(rawKey.trim());

        ApiKey cached = apiKeyCache.getIfPresent(keyHash);
        if (cached != null) {
            return Optional.of(cached);
        }

        Optional<ApiKey> apiKeyOpt = apiKeyRepository.findByKeyHash(keyHash);
        apiKeyOpt.ifPresent(apiKey -> apiKeyCache.put(keyHash, apiKey));
        return apiKeyOpt;
    }

    /**
     * Registra el uso de la clave actualizando el timestamp lastUsedAt.
     */
    @Transactional
    public void recordKeyUsage(ApiKey apiKey) {
        apiKey.setLastUsedAt(OffsetDateTime.now());
        apiKeyRepository.save(apiKey);
    }

    /**
     * Invalida completamente la caché de API Keys (útil para pruebas y sincronización).
     */
    public void clearCache() {
        apiKeyCache.invalidateAll();
    }
}
