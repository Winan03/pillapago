package com.pillapago.rules.domain;

/**
 * Estado del ciclo de vida de una versión de conjunto de reglas (HU-08).
 * - ACTIVE: Versión en vigor utilizada por el motor en memoria para evaluar transacciones.
 * - ARCHIVED: Versión histórica preservada para auditoría forense o posible rollback.
 */
public enum RuleSetVersionStatus {
    ACTIVE,
    ARCHIVED
}
