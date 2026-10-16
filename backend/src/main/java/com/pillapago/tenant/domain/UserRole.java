package com.pillapago.tenant.domain;

/**
 * Roles disponibles en el sistema según la matriz RBAC de Pillapago:
 * MERCHANT_ADMIN, RISK_ANALYST, AUDITOR.
 */
public enum UserRole {
    MERCHANT_ADMIN,
    RISK_ANALYST,
    AUDITOR
}
