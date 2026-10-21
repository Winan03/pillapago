package com.pillapago.shared.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Adaptador local del puerto ClavesPort.
 * Emula un servicio KMS (como AWS KMS / Floci) realizando cifrado de sobre (Envelope Encryption)
 * de las claves de datos con AES-256-GCM y una clave maestra (KEK) inyectada.
 */
@Component
public class LocalKmsClavesAdapter implements ClavesPort {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int IV_LENGTH_BYTES = 12;

    private final SecretKey masterKey;
    private final String masterKeyId;
    private final SecureRandom secureRandom = new SecureRandom();

    public LocalKmsClavesAdapter(
            @Value("${pillapago.crypto.master-kms-key:pillapago-default-master-kms-kek-256-bits-super-secret}")
            String masterKeyString,
            @Value("${pillapago.crypto.master-kms-key-id:local-kms-master-v1}")
            String masterKeyId) {
        this.masterKeyId = masterKeyId;
        this.masterKey = deriveKey(masterKeyString);
    }

    @Override
    public byte[] encryptMaster(byte[] plaintext) {
        if (plaintext == null) {
            return new byte[0];
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, spec);
            cipher.updateAAD(masterKeyId.getBytes(StandardCharsets.UTF_8));

            byte[] ciphertext = cipher.doFinal(plaintext);

            // Estructura: [IV (12 bytes)] + [Ciphertext + Tag]
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            byteBuffer.put(iv);
            byteBuffer.put(ciphertext);
            return byteBuffer.array();
        } catch (Exception e) {
            throw new IllegalStateException("Error al cifrar clave de datos con ClavesPort (KMS)", e);
        }
    }

    @Override
    public byte[] decryptMaster(byte[] combinedCiphertext) {
        if (combinedCiphertext == null || combinedCiphertext.length < IV_LENGTH_BYTES) {
            return new byte[0];
        }
        try {
            ByteBuffer byteBuffer = ByteBuffer.wrap(combinedCiphertext);
            byte[] iv = new byte[IV_LENGTH_BYTES];
            byteBuffer.get(iv);

            byte[] ciphertext = new byte[byteBuffer.remaining()];
            byteBuffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, masterKey, spec);
            cipher.updateAAD(masterKeyId.getBytes(StandardCharsets.UTF_8));

            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            throw new IllegalStateException("Error al descifrar clave de datos con ClavesPort (KMS): " + e.getMessage(), e);
        }
    }

    @Override
    public String getMasterKeyId() {
        return masterKeyId;
    }

    private SecretKey deriveKey(String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = digest.digest(secret.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(keyBytes, "AES");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo SHA-256 no disponible", e);
        }
    }
}
