package com.pillapago.shared.crypto;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Servicio asíncrono para operaciones de cifrado fuera de la ruta crítica (hot path).
 * Permite que los procesos de evaluación de transacciones respondan en milisegundos sin
 * bloquearse en operaciones criptográficas pesadas de persistencia.
 */
@Service
public class AsyncFieldEncryptionService {

    private final FieldEncryptor fieldEncryptor;

    public AsyncFieldEncryptionService(FieldEncryptor fieldEncryptor) {
        this.fieldEncryptor = fieldEncryptor;
    }

    /**
     * Cifra un campo sensible de forma asíncrona retornando un CompletableFuture.
     */
    @Async
    public CompletableFuture<String> encryptStringAsync(
            UUID merchantId, String plaintext, String table, String column, Object rowId) {
        String ciphertext = fieldEncryptor.encryptString(merchantId, plaintext, table, column, rowId);
        return CompletableFuture.completedFuture(ciphertext);
    }

    /**
     * Descifra un campo sensible de forma asíncrona retornando un CompletableFuture.
     */
    @Async
    public CompletableFuture<String> decryptStringAsync(
            UUID merchantId, String ciphertextBase64, String table, String column, Object rowId) {
        String plaintext = fieldEncryptor.decryptString(merchantId, ciphertextBase64, table, column, rowId);
        return CompletableFuture.completedFuture(plaintext);
    }
}
