# HU-09 · Resolver un caso en revisión — Resumen de Implementación

## ¿Qué hace este módulo?

Permite que un analista de riesgo (`RISK_ANALYST`) revise las transacciones marcadas como sospechosas por el motor antifraude. El analista puede ver la cola de casos, tomar uno para sí mismo y luego aprobar o rechazar la transacción. Si dos analistas intentan resolver el mismo caso al mismo tiempo, el sistema garantiza que solo uno tenga éxito.

---

## Flujo en 3 clics (requisito de ergonomía)

| Clic | Acción | Endpoint |
|------|--------|----------|
| 1 | Abrir el detalle de una transacción sospechosa desde la cola | `GET /v1/cases/{id}` |
| 2 | Seleccionar "Aprobar" o "Rechazar" en el modal | — |
| 3 | Confirmar con una justificación obligatoria | `POST /v1/cases/{id}/resolve` |

---

## Endpoints implementados

| Endpoint | Rol requerido | Descripción |
|----------|---------------|-------------|
| `GET /v1/cases` | RISK_ANALYST, MERCHANT_ADMIN | Lista casos abiertos del propio comercio con filtros opcionales |
| `GET /v1/cases/{id}` | RISK_ANALYST, MERCHANT_ADMIN | Detalle completo: score, reglas activadas, versión de reglas |
| `POST /v1/cases/{id}/claim` | RISK_ANALYST | Asigna el caso al analista autenticado → estado `ASSIGNED` |
| `POST /v1/cases/{id}/resolve` | RISK_ANALYST | Resuelve como `APPROVED` o `REJECTED` con comentario obligatorio |

---

## Ciclo de vida del caso

```
PENDING_REVIEW  ──(claim)──►  ASSIGNED  ──(resolve)──►  RESOLVED
                                                              │
                                                         (inmutable)
```

Una vez que un caso llega a `RESOLVED`, cualquier intento de modificarlo devuelve **409 Conflict**.

---

## Control de concurrencia: ¿cómo evita doble resolución?

Se usa **bloqueo optimista JPA** con el campo `@Version Integer version` en la entidad `CaseEntity`.

- Cuando JPA guarda el caso, ejecuta: `UPDATE cases SET ... WHERE id = ? AND version = ?`
- Si otro analista ya modificó el caso antes, la versión en base de datos ya no coincide → `Rows affected: 0`
- JPA lanza `OptimisticLockingFailureException`
- El `GlobalExceptionHandler` la captura y responde **409 Conflict**

> **Para un junior:** Es como editar un documento compartido — si alguien más guardó antes que tú, te avisa que hay conflicto y no aplica tu cambio.

---

## Aislamiento multi-tenant (seguridad)

Todos los endpoints usan el `merchantId` del usuario autenticado (extraído del JWT) para filtrar los datos. Un analista del Comercio A **nunca** puede ver ni resolver casos del Comercio B.

---

## Archivos creados o modificados

### Backend

| Archivo | Rol |
|---------|-----|
| `V11__case_review_resolution_schema.sql` | Migración Flyway: agrega columnas `version`, `resolution`, `resolution_notes`, `resolved_by`, `resolved_at` |
| `CaseStatus.java` | Enum: `PENDING_REVIEW`, `ASSIGNED`, `RESOLVED` |
| `CaseResolution.java` | Enum: `APPROVED`, `REJECTED` |
| `CaseEntity.java` | Entidad JPA con `@Version` para bloqueo optimista |
| `CaseRepository.java` | Query JPQL con filtros de antigüedad, monto y estado |
| `CaseService.java` | Lógica de negocio: getCases, claimCase, resolveCase |
| `CaseController.java` | REST endpoints con `@PreAuthorize` |
| `ResolveCaseRequest.java` | DTO con validación `@NotBlank` en el comentario |
| `CaseSummaryDto.java` | DTO para la lista de la cola |
| `CaseDetailDto.java` | DTO con score, reglas y versión de reglas |
| `CaseFilterRequest.java` | Parámetros de filtro (ageDays, minAmount, maxAmount, status) |
| `CaseAlreadyResolvedException.java` | Excepción de inmutabilidad → 409 |
| `GlobalExceptionHandler.java` | Manejadores para OptimisticLockingFailureException y CaseAlreadyResolvedException |

### Frontend

| Archivo | Rol |
|---------|-----|
| `src/api/cases.ts` | Cliente API tipado para todos los endpoints HU-09 |
| `src/styles/cases.css` | Estilos específicos del módulo (sin CSS inline) |
| `src/components/cases/CaseDetailModal.tsx` | Modal con detalle, reglas y botones de resolución |
| `src/pages/CasesPage.tsx` | Página de cola de casos con filtros y KPIs |

### Tests

| Archivo | Tipo | Resultado |
|---------|------|-----------|
| `CaseResolutionE2ETest.java` | E2E — 7 criterios de aceptación | ✅ 7/7 PASSING |
| `CaseOptimisticLockingConcurrencyTest.java` | Concurrencia real con 2 hilos | ✅ 1/1 PASSING |

### Diagrama

| Archivo |
|---------|
| `docs/diagrams/hu09_case_resolution_lifecycle_e2e.svg` |

---

## Criterios de aceptación — Estado final

| # | Criterio | Estado |
|---|----------|--------|
| C1 | `GET /v1/cases` con filtros por antigüedad y monto, solo del comercio del usuario | ✅ Cumplido |
| C2 | `POST /v1/cases/{id}/claim` asigna el caso al analista (estado ASSIGNED) | ✅ Cumplido |
| C3 | `POST /v1/cases/{id}/resolve` exige comentario obligatorio (min 5 chars) | ✅ Cumplido |
| C4 | Dos analistas no pueden resolver el mismo caso: bloqueo optimista → 409 | ✅ Cumplido |
| C5 | Un caso resuelto no se puede modificar (inmutabilidad) | ✅ Cumplido |
| C6 | El detalle muestra score, reglas activadas y versión de reglas | ✅ Cumplido |
| C7 | Resolver un caso toma 3 clics o menos desde la cola | ✅ Cumplido |
| C8 | Pantallas React: cola de casos (`CasesPage`) y detalle con acciones (`CaseDetailModal`) | ✅ Cumplido |

---

## Decisiones técnicas clave

- **`@Version` en lugar de locks pesimistas:** El volumen de casos no justifica bloqueos de BD. El optimista es más eficiente y escala mejor.
- **`CaseAlreadyResolvedException` separado de `OptimisticLockingFailureException`:** Ambos retornan 409, pero tienen causas distintas — claridad en los logs y trazabilidad.
- **Sin CSS inline en el frontend:** Todos los estilos están en `cases.css` separado del CSS general del sistema.
- **Sin emojis:** Todo el diseño usa iconos de la librería `lucide-react` para mantener el estándar profesional del sistema.
- **Tests autónomos:** El test de inmutabilidad crea su propio caso dedicado para no depender del estado que dejan otros tests.

---

## Reporte E2E generado

El archivo `backend/target/reporte_hu09_resolucion_casos.txt` contiene la evidencia completa de cada criterio verificado con los valores reales de la ejecución.
