package com.pillapago.shared.crypto;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.crypto.tink.Aead;
import com.google.crypto.tink.InsecureSecretKeyAccess;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.RegistryConfiguration;
import com.google.crypto.tink.TinkProtoKeysetFormat;
import com.google.crypto.tink.aead.AeadConfig;
import com.google.crypto.tink.aead.PredefinedAeadParameters;
import com.pillapago.tenant.domain.Merchant;
import com.pillapago.tenant.domain.TenantKey;
import com.pillapago.tenant.repository.MerchantRepository;
import com.pillapago.tenant.repository.TenantKeyRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Gestor del ciclo de vida de claves criptográficas por comercio (DEK y Blind Index Key).
 * Implementa cifrado de sobre (Envelope Encryption - RNF-31) mediante ClavesPort (KMS),
 * y mantiene una caché de alto rendimiento en memoria con un TTL de 15 minutos (Caffeine).
 * Soporta crypto-shredding (borrado irreversible de claves para destrucción de datos).
 */
@Service
public class TenantKeyManager {

    private final TenantKeyRepository tenantKeyRepository;
    private final MerchantRepository merchantRepository;
    private final ClavesPort clavesPort;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Caché en memoria para claves de datos (DEK) e índices ciegos con TTL de 15 minutos.
     */
    private final Cache<UUID, CachedTenantCrypto> cache = Caffeine.newBuilder()
            .expireAfterWrite(15, TimeUnit.MINUTES)
            .maximumSize(10_000)
            .recordStats()
            .build();

    public record CachedTenantCrypto(Aead aead, byte[] blindIndexKey, int keyVersion) {}

    public TenantKeyManager(
            TenantKeyRepository tenantKeyRepository,
            MerchantRepository merchantRepository,
            ClavesPort clavesPort) {
        this.tenantKeyRepository = tenantKeyRepository;
        this.merchantRepository = merchantRepository;
        this.clavesPort = clavesPort;
    }

    @PostConstruct
    public void init() {
        try {
            AeadConfig.register();
        } catch (GeneralSecurityException e) {
            throw new CryptoOperationException("Fallo al inicializar Google Tink AeadConfig", e);
        }
    }

    /**
     * Obtiene o genera la primitiva AEAD activa para un comercio específico (utilizado al cifrar).
     */
    @Transactional
    public Aead getOrCreateActiveAead(UUID merchantId) {
        return getOrCreateTenantCrypto(merchantId).aead();
    }

    /**
     * Obtiene la primitiva AEAD activa existente para un comercio (utilizado al descifrar).
     * Si las claves fueron destruidas (crypto-shredded) o no existen, lanza CryptoOperationException.
     */
    @Transactional(readOnly = true)
    public Aead getActiveAead(UUID merchantId) {
        if (merchantId == null) {
            throw new IllegalArgumentException("merchantId no puede ser nulo");
        }
        CachedTenantCrypto cached = cache.getIfPresent(merchantId);
        if (cached != null) {
            return cached.aead();
        }

        TenantKey tenantKey = tenantKeyRepository.findByMerchantIdAndActiveTrue(merchantId)
                .orElseThrow(() -> new CryptoOperationException(
                        "No existe clave criptográfica activa para el comercio " + merchantId +
                        " (posiblemente destruida por crypto-shredding o inexistente)"));

        CachedTenantCrypto loaded = loadFromTenantKey(tenantKey);
        cache.put(merchantId, loaded);
        return loaded.aead();
    }

    /**
     * Obtiene o genera la clave activa de 256 bits para índices ciegos (HMAC) de un comercio.
     */
    @Transactional
    public byte[] getOrCreateActiveBlindIndexKey(UUID merchantId) {
        return getOrCreateTenantCrypto(merchantId).blindIndexKey();
    }

    /**
     * Obtiene el contenedor de criptografía del comercio desde la caché o desde la BD.
     * Si no existe en la BD, genera un nuevo juego de claves DEK y Blind Index Key.
     */
    @Transactional
    public CachedTenantCrypto getOrCreateTenantCrypto(UUID merchantId) {
        if (merchantId == null) {
            throw new IllegalArgumentException("merchantId no puede ser nulo");
        }

        CachedTenantCrypto cached = cache.getIfPresent(merchantId);
        if (cached != null) {
            return cached;
        }

        // Buscar en base de datos
        TenantKey tenantKey = tenantKeyRepository.findByMerchantIdAndActiveTrue(merchantId)
                .orElseGet(() -> createNewTenantKey(merchantId));

        CachedTenantCrypto loaded = loadFromTenantKey(tenantKey);
        cache.put(merchantId, loaded);
        return loaded;
    }

    /**
     * Genera un nuevo DEK (Google Tink AES-256-GCM) y clave de índice ciego para el comercio,
     * protegiendo el DEK con el KEK maestro de KMS (cifrado de sobre).
     */
    private TenantKey createNewTenantKey(UUID merchantId) {
        try {
            // 1. Generar nuevo KeysetHandle de Google Tink para AES256_GCM
            KeysetHandle keysetHandle = KeysetHandle.generateNew(PredefinedAeadParameters.AES256_GCM);
            byte[] serializedDek = TinkProtoKeysetFormat.serializeKeyset(keysetHandle, InsecureSecretKeyAccess.get());

            // 2. Cifrar el DEK con la clave maestra KEK (Envelope Encryption)
            byte[] encryptedDek = clavesPort.encryptMaster(serializedDek);

            // 3. Generar clave independiente de 256 bits para índices ciegos HMAC-SHA256
            byte[] blindIndexKey = new byte[32];
            secureRandom.nextBytes(blindIndexKey);

            // 4. Obtener referencia del comercio
            Merchant merchant = merchantRepository.findById(merchantId)
                    .orElseGet(() -> merchantRepository.getReferenceById(merchantId));

            TenantKey keyEntity = TenantKey.builder()
                    .merchant(merchant)
                    .keyVersion(1)
                    .encryptedDekBytes(encryptedDek)
                    .blindIndexKeyBytes(blindIndexKey)
                    .active(true)
                    .build();

            return tenantKeyRepository.save(keyEntity);
        } catch (GeneralSecurityException e) {
            throw new CryptoOperationException("Error al generar juego de claves para el comercio " + merchantId, e);
        }
    }

    /**
     * Reconstruye la primitiva Tink Aead a partir de los bytes cifrados almacenados en BD.
     */
    private CachedTenantCrypto loadFromTenantKey(TenantKey tenantKey) {
        try {
            // 1. Descifrar el DEK usando la clave maestra KEK
            byte[] serializedDek = clavesPort.decryptMaster(tenantKey.getEncryptedDekBytes());

            // 2. Deserializar el KeysetHandle de Google Tink
            KeysetHandle keysetHandle = TinkProtoKeysetFormat.parseKeyset(serializedDek, InsecureSecretKeyAccess.get());
            Aead aead = keysetHandle.getPrimitive(RegistryConfiguration.get(), Aead.class);

            return new CachedTenantCrypto(aead, tenantKey.getBlindIndexKeyBytes(), tenantKey.getKeyVersion());
        } catch (GeneralSecurityException e) {
            throw new CryptoOperationException("Error al instanciar primitiva AEAD para el comercio " + tenantKey.getMerchant().getId(), e);
        }
    }

    /**
     * Crypto-shredding: Elimina de forma permanente las claves criptográficas del comercio.
     * Esto hace que todos los datos previamente cifrados sean matemáticamente irrecuperables.
     */
    @Transactional
    public void cryptoShred(UUID merchantId) {
        if (merchantId == null) return;
        tenantKeyRepository.deleteByMerchantId(merchantId);
        cache.invalidate(merchantId);
    }

    /**
     * Invalida la entrada del comercio en la caché en memoria (útil para pruebas de recarga y rotación).
     */
    public void evictCache(UUID merchantId) {
        if (merchantId != null) {
            cache.invalidate(merchantId);
        }
    }

    /**
     * Retorna el número aproximado de comercios en la caché en memoria.
     */
    public long getCacheSize() {
        return cache.estimatedSize();
    }
}
