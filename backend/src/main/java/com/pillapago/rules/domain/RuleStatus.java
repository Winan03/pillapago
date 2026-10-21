package com.pillapago.rules.domain;

/**
 * Estado del ciclo de vida de una regla en Pillapago:
 * - DRAFT: Borrador en edición, no afecta decisiones en vivo (HU-07).
 * - PUBLISHED: Activa y en vigor en un conjunto de reglas versionado (HU-08).
 * - ARCHIVED: Histórica o desactivada por una nueva versión.
 */
public enum RuleStatus {
    DRAFT,
    PUBLISHED,
    ARCHIVED
}
