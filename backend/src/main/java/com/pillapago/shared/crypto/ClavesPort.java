package com.pillapago.shared.crypto;

/**
 * Puerto de abstracción para la gestión de claves maestras (KMS).
 * Permite desacoplar el cifrado de sobre (Envelope Encryption) del proveedor
 * específico (AWS KMS en producción/Floci en local, o secreto en nube gratuita - ADR-06).
 */
public interface ClavesPort {

    /**
     * Cifra una clave de datos (DEK) utilizando la clave maestra (KEK).
     */
    byte[] encryptMaster(byte[] plaintext);

    /**
     * Descifra una clave de datos (DEK) utilizando la clave maestra (KEK).
     */
    byte[] decryptMaster(byte[] ciphertext);

    /**
     * Identificador o alias de la clave maestra activa.
     */
    String getMasterKeyId();
}
