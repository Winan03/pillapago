/**
 * Criptografía centralizada — regla: ningún otro módulo cifra por su cuenta.
 *
 *   FieldEncryptor   → AES-256-GCM con Google Tink + ClavesPort (KMS / secreto)
 *   BlindIndexer     → HMAC-SHA256 para índices ciegos y claves de Redis
 *   DataMasker       → Enmascara IP, deviceId, customerId, cardFingerprint
 */
package com.pillapago.shared.crypto;
