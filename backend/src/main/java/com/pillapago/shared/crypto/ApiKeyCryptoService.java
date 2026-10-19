package com.pillapago.shared.crypto;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Servicio criptográfico para generación segura y cálculo de hash SHA-256
 * de las API keys del comercio (RNF-12).
 */
@Service
public class ApiKeyCryptoService {

    public static final String KEY_PREFIX = "pp_live_";
    private static final int ENTROPY_BYTES = 32; // 256 bits de entropía criptográfica
    private static final int PREFIX_DISPLAY_LENGTH = 16; // Ej. "pp_live_a1b2c3d4"

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Genera una clave aleatoria segura con prefijo "pp_live_".
     * Longitud resultante: 8 (prefijo) + 64 (hex) = 72 caracteres.
     */
    public String generateRawKey() {
        byte[] randomBytes = new byte[ENTROPY_BYTES];
        secureRandom.nextBytes(randomBytes);
        String randomHex = HexFormat.of().formatHex(randomBytes);
        return KEY_PREFIX + randomHex;
    }

    /**
     * Extrae el fragmento identificatorio para visualización segura en dashboard (ej: pp_live_a1b2c3d4).
     */
    public String extractPrefix(String rawKey) {
        if (rawKey == null) {
            return KEY_PREFIX;
        }
        int endIndex = Math.min(PREFIX_DISPLAY_LENGTH, rawKey.length());
        return rawKey.substring(0, endIndex);
    }

    /**
     * Calcula el digest SHA-256 en formato hexadecimal de 64 caracteres en minúsculas.
     */
    public String computeSha256(String rawKey) {
        if (rawKey == null) {
            throw new IllegalArgumentException("La clave para calcular el hash no puede ser nula");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo SHA-256 no disponible en la JVM", e);
        }
    }

    /**
     * Comprobación en tiempo constante para mitigar ataques de temporización (timing attacks).
     */
    public boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8)
        );
    }
}
