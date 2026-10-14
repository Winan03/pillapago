/**
 * Módulo integration — Puertos y adaptadores (patrón hexagonal).
 *
 * Puertos definidos aquí, implementaciones seleccionadas por perfil de Spring:
 *   ColaPort     → SQS (Floci / local) | Outbox Postgres (cloud)
 *   ClavesPort   → KMS (Floci / local) | Secreto de Render (cloud)
 *   ArchivosPort → S3 (Floci / local)  | Cloudflare R2 (cloud)   [Fase 2]
 *   CorreoPort   → SES (Floci / local) | Resend / Brevo (cloud)
 *
 * Regla: el código de negocio NUNCA importa el SDK de AWS directamente.
 */
package com.pillapago.integration;
