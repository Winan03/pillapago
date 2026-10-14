/**
 * Módulo shared — Código transversal reutilizable por todos los módulos.
 *
 * Sub-paquetes:
 *   config/     → Configuración de Spring Security, Redis, CORS, OpenAPI
 *   exception/  → @RestControllerAdvice + ProblemDetail (RFC 7807)
 *   security/   → Filtros JWT y API key
 *   crypto/     → Cifrado de campo (Tink + KMS), HMAC-SHA256, enmascarado
 *   util/       → Hashing, fechas, utilidades puras sin dependencias de Spring
 *
 * Regla: shared NO depende de ningún módulo de negocio (auth, tenant, evaluation, etc.)
 * Regla: TODO el cifrado/descifrado y HMAC pasa por shared/crypto, nunca directo.
 */
package com.pillapago.shared;
