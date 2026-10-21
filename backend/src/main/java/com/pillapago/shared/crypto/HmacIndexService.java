package com.pillapago.shared.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Servicio criptográfico de índices ciegos y seudonimización (RNF-32).
 * Transforma identificadores sensibles (dispositivo, IP, correo, cuenta, etc.)
 * en tokens ciegos mediante HMAC-SHA256 con claves por comercio, garantizando
 * que Redis y las estructuras en memoria nunca contengan datos personales en claro.
 */
@Service
public class HmacIndexService {

    private static final String HMAC_SHA256_ALGORITHM = "HmacSHA256";
    private static final String SHA256_ALGORITHM = "SHA-256";

    private final byte[] masterBlindIndexSecret;
    private final TenantKeyManager tenantKeyManager;

    public HmacIndexService(
            @Value("${pillapago.crypto.blind-index-master-key:pillapago-super-secret-blind-index-master-key-256-bits-minimum}")
            String masterKey,
            @Lazy
            TenantKeyManager tenantKeyManager) {
        this.masterBlindIndexSecret = masterKey.getBytes(StandardCharsets.UTF_8);
        this.tenantKeyManager = tenantKeyManager;
    }

    /**
     * Calcula el índice ciego HMAC-SHA256 estándar para un identificador sensible y un comercio.
     * Utiliza derivación determinista a partir del merchantId y la clave maestra (utilizado en Redis velocity y caché).
     * Retorna una cadena hexadecimal en minúsculas de 64 caracteres.
     */
    public String computeBlindIndex(UUID merchantId, String rawIdentifier) {
        if (merchantId == null) {
            throw new IllegalArgumentException("merchantId no puede ser nulo para cálculo de índice ciego");
        }
        if (rawIdentifier == null || rawIdentifier.trim().isEmpty()) {
            return "";
        }

        try {
            byte[] merchantKeyBytes = deriveMerchantKey(merchantId);
            return computeBlindIndexWithKey(merchantKeyBytes, rawIdentifier);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Error al calcular HMAC-SHA256 para índice ciego", e);
        }
    }

    /**
     * Calcula el índice ciego HMAC-SHA256 utilizando la clave de índice específica y versionada
     * del comercio gestionada en base de datos (tenant_keys), garantizando separación estricta
     * con la clave de cifrado de datos DEK.
     */
    public String computeTenantBlindIndex(UUID merchantId, String rawIdentifier) {
        if (merchantId == null) {
            throw new IllegalArgumentException("merchantId no puede ser nulo");
        }
        if (rawIdentifier == null || rawIdentifier.trim().isEmpty()) {
            return "";
        }
        byte[] tenantKey = tenantKeyManager.getOrCreateActiveBlindIndexKey(merchantId);
        return computeBlindIndexWithKey(tenantKey, rawIdentifier);
    }

    /**
     * Calcula el índice ciego HMAC-SHA256 utilizando una clave específica de 256 bits.
     */
    public String computeBlindIndexWithKey(byte[] blindIndexKey, String rawIdentifier) {
        if (rawIdentifier == null || rawIdentifier.trim().isEmpty()) {
            return "";
        }
        if (blindIndexKey == null || blindIndexKey.length == 0) {
            throw new IllegalArgumentException("blindIndexKey no puede ser nula o vacía");
        }
        try {
            SecretKeySpec secretKeySpec = new SecretKeySpec(blindIndexKey, HMAC_SHA256_ALGORITHM);
            Mac mac = Mac.getInstance(HMAC_SHA256_ALGORITHM);
            mac.init(secretKeySpec);
            byte[] hmacBytes = mac.doFinal(rawIdentifier.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hmacBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Error al calcular HMAC-SHA256 con clave de comercio", e);
        }
    }

    /**
     * Calcula el hash SHA-256 canónico de un texto (ej. payload para idempotencia).
     */
    public String computeSha256(String canonicalPayload) {
        if (canonicalPayload == null) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance(SHA256_ALGORITHM);
            byte[] hash = digest.digest(canonicalPayload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo SHA-256 no disponible", e);
        }
    }

    /**
     * Deriva una clave específica de 256 bits para el comercio combinando la clave maestra
     * con el identificador único del merchant.
     */
    private byte[] deriveMerchantKey(UUID merchantId) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance(SHA256_ALGORITHM);
        md.update(masterBlindIndexSecret);
        md.update(merchantId.toString().getBytes(StandardCharsets.UTF_8));
        return md.digest();
    }
}
