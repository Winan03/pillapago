package com.pillapago.lists.domain;

/**
 * Clasificación de la lista de control de acceso (HU-13 / RF-09).
 * - BLOCK: Lista negra. Coincidencia genera REJECT inmediato en evaluación.
 * - ALLOW: Lista blanca/confianza. Coincidencia genera APPROVE inmediato o bypass de reglas.
 */
public enum ListType {
    BLOCK,
    ALLOW
}
