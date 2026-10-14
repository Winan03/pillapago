/**
 * Módulo messaging — Productores y consumidores de eventos sobre ColaPort.
 *
 * Puerto ColaPort:
 *   - Adaptador local:  SQS contra Floci (emulador AWS)
 *   - Adaptador cloud:  Outbox en PostgreSQL (FOR UPDATE SKIP LOCKED)
 *
 * Fase 5 de la hoja de ruta · ADR-01, ADR-08
 */
package com.pillapago.messaging;
