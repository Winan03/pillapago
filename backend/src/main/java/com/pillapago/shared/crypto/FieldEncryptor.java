package com.pillapago.shared.crypto;

import com.google.crypto.tink.Aead;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.UUID;

/**
 * Servicio centralizado de cifrado a nivel de campo con Google Tink (AES-256-GCM).
 * Implementa cifrado de sobres por comercio y autenticación estricta con Datos Asociados (AEAD AAD).
 */
@Service
public class FieldEncryptor {

    private final TenantKeyManager tenantKeyManager;

    public FieldEncryptor(TenantKeyManager tenantKeyManager) {
        this.tenantKeyManager = tenantKeyManager;
    }

    /**
     * Cifra un arreglo de bytes utilizando la clave activa del comercio y datos asociados.
     */
    public byte[] encrypt(UUID merchantId, byte[] plaintext, byte[] associatedData) {
        if (plaintext == null) {
            return null;
        }
        if (associatedData == null || associatedData.length == 0) {
            throw new IllegalArgumentException("Associated Data (AAD) es obligatorio para el cifrado de campo");
        }
        try {
            Aead aead = tenantKeyManager.getOrCreateActiveAead(merchantId);
            return aead.encrypt(plaintext, associatedData);
        } catch (GeneralSecurityException e) {
            throw new CryptoOperationException("Fallo en la operación de cifrado Google Tink AES-256-GCM", e);
        }
    }

    /**
     * Descifra un arreglo de bytes utilizando la clave activa del comercio y datos asociados.
     * Si los datos asociados no coinciden o el contenido fue alterado, lanza AssociatedDataValidationException.
     * Si las claves del comercio fueron destruidas (crypto-shredding), lanza CryptoOperationException.
     */
    public byte[] decrypt(UUID merchantId, byte[] ciphertext, byte[] associatedData) {
        if (ciphertext == null) {
            return null;
        }
        if (associatedData == null || associatedData.length == 0) {
            throw new IllegalArgumentException("Associated Data (AAD) es obligatorio para el descifrado de campo");
        }
        try {
            Aead aead = tenantKeyManager.getActiveAead(merchantId);
            return aead.decrypt(ciphertext, associatedData);
        } catch (GeneralSecurityException e) {
            throw new AssociatedDataValidationException(
                    "Fallo de verificación de autenticación AEAD: el texto cifrado ha sido manipulado " +
                    "o los datos asociados no coinciden (posible intento de copia entre filas/comercios)", e);
        }
    }

    /**
     * Cifra un texto sensible vinculándolo criptográficamente a su ubicación (tabla, columna y fila).
     * Retorna el texto cifrado codificado en Base64.
     */
    public String encryptString(UUID merchantId, String plaintext, String table, String column, Object rowId) {
        if (plaintext == null) {
            return null;
        }
        byte[] aad = AssociatedDataBuilder.build(merchantId, table, column, rowId);
        byte[] ciphertext = encrypt(merchantId, plaintext.getBytes(StandardCharsets.UTF_8), aad);
        return Base64.getEncoder().encodeToString(ciphertext);
    }

    /**
     * Descifra un texto sensible codificado en Base64 validando que provenga exactamente
     * de la misma ubicación de tabla, columna y fila.
     */
    public String decryptString(UUID merchantId, String ciphertextBase64, String table, String column, Object rowId) {
        if (ciphertextBase64 == null) {
            return null;
        }
        byte[] aad = AssociatedDataBuilder.build(merchantId, table, column, rowId);
        byte[] ciphertextBytes = Base64.getDecoder().decode(ciphertextBase64);
        byte[] decryptedBytes = decrypt(merchantId, ciphertextBytes, aad);
        return new String(decryptedBytes, StandardCharsets.UTF_8);
    }
}
