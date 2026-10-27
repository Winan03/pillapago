# HU-13 · Listas de Bloqueo y de Confianza — Resumen de Implementación

## ¿Qué hace este módulo?

Permite que un analista de riesgo (`RISK_ANALYST`) bloquee (`BLOCK`) o permita (`ALLOW`) de forma inmediata cualquier dispositivo, dirección IP, correo electrónico o huella digital de tarjeta (`CARD_FINGERPRINT`). 

Cuando un elemento se agrega a la lista de bloqueo (`BLOCK`), cualquier transacción futura que provenga de ese elemento es **rechazada de inmediato (`REJECT`)** en una fracción de milisegundo ($O(1)$ en memoria RAM), sin necesidad de consultar bases de datos relacionales ni ejecutar el motor de reglas complejas. De igual manera, si está en lista de confianza (`ALLOW`), la transacción es **aprobada de inmediato (`APPROVE`)**.

---

## Criterios de Aceptación Verificados

| Criterio | Exigencia | Implementación | Estado |
|----------|-----------|----------------|:------:|
| **1. Entrada** | Tipo (`DEVICE`, `IP`, `EMAIL`, `CARD_FINGERPRINT`), valor, lista (`BLOCK` o `ALLOW`), motivo obligatorio y vencimiento opcional. | `CreateListEntryRequest` con anotaciones `@NotNull`, `@NotBlank`, `@Size(min=5)` y `@Future`. | Cumplido |
| **2. Respuesta inmediata** | Responde `201 Created` y el valor se aplica en la siguiente evaluación. | `POST /v1/lists` sincroniza atómicamente la memoria RAM (`ListCacheService`) al persistir. | Cumplido |
| **3. Rechazo en BLOCK** | Coincidencia en `BLOCK` devuelve `REJECT` inmediato en la evaluación. | Short-circuit en el Hot Path (`TransactionEvaluationService.checkInMemoryLists`) con Score 100. | Cumplido |
| **4. Consulta en memoria** | Las listas se consultan desde una caché en memoria. | `ListCacheService` mantiene sets de búsqueda en memoria RAM con acceso de complejidad $O(1)$. | Cumplido |
| **5. RBAC estricto** | Solo el `RISK_ANALYST` las gestiona (`403 Forbidden` para otros roles). | Protegido con Spring Security `@PreAuthorize("hasRole('RISK_ANALYST')")`. | Cumplido |
| **6. Auditoría lista** | Cada alta o baja queda en auditoría (conexión lista para HU-19). | Logger estructurado `AUDIT_LOGGER` registra comercio, analista, tipo de lista y motivo. | Cumplido |
| **7. Privacidad y Seguridad** | RNF-15: Blind index y cifrado de datos sensibles en reposo. | HMAC-SHA256 para búsqueda exacta + Google Tink AES-256-GCM + enmascaramiento visual. | Cumplido |

---

## Arquitectura del Hot Path y Caché en Memoria ($O(1)$)

```
[ POST /v1/evaluations ]
         │
         ▼
┌─────────────────────────────────────────────────────────────┐
│ 1. Consulta en Memoria RAM O(1) (ListCacheService)          │
│    - Extrae hashes HMAC de Device, IP, Email y Tarjeta       │
│    - Verifica expiración temporal automática                 │
└─────────────────────────────────────────────────────────────┘
         │
         ├──► [Coincide en BLOCKLIST] ──► REJECT Inmediato (Score: 100) ──► Fin (0ms agregado)
         │
         ├──► [Coincide en ALLOWLIST] ──► APPROVE Inmediato (Score: 0)  ──► Fin (0ms agregado)
         │
         ▼ [Sin coincidencias en listas]
┌─────────────────────────────────────────────────────────────┐
│ 2. Pipeline Estándar: Redis (Velocidad/Idempotencia)         │
│ 3. Motor de Reglas y Scoring Dinámico (Drools/RuleEngine)   │
└─────────────────────────────────────────────────────────────┘
```

> **Para un junior:** En vez de ir al disco duro o consultar bases de datos cada vez que pasa una tarjeta, guardamos los números prohibidos en una lista ultrarrápida en la memoria RAM del servidor. Si el defraudador aparece, lo frenamos en la puerta sin gastar recursos del sistema.
>
> **Para un senior:** Las entradas se indexan por `merchantId` en un `ConcurrentHashMap` respaldado por `AtomicReference<MerchantActiveLists>`. Las operaciones de lectura son libres de bloqueos (lock-free), permitiendo que la evaluación de concurrencia masiva no sufra contención de hilos.

---

## Privacidad y Criptografía (RNF-15)

Para cumplir con normativas de protección de datos (PCI-DSS / GDPR):
1. **Índice Ciego (Blind Index):** Se computa un hash determinista con `HmacIndexService` usando una clave criptográfica aislada. Permite buscar coincidencias exactas en base de datos y memoria sin almacenar el dato sensible en texto claro.
2. **Cifrado en Reposo:** El dato sensible se cifra con **Google Tink (AES-256-GCM)** en la columna `encrypted_value`.
3. **Enmascaramiento Dinámico:** Los analistas solo visualizan datos parcialmente ocultos (`203.0.***.***`, `u***r@correo.com`), impidiendo fuga de datos personales en pantallas o logs.

---

## Endpoints REST Creados

Todos bajo el prefijo `/v1/lists`:

| Método | Endpoint | Rol Requerido | Descripción |
|--------|----------|---------------|-------------|
| `POST` | `/v1/lists` | `RISK_ANALYST` | Crea una regla de bloqueo o confianza. Retorna `201 Created`. |
| `GET` | `/v1/lists` | `RISK_ANALYST` | Lista todas las entradas activas del comercio con valores enmascarados. |
| `GET` | `/v1/lists/{id}` | `RISK_ANALYST` | Obtiene el detalle de una entrada específica del comercio. |
| `DELETE` | `/v1/lists/{id}` | `RISK_ANALYST` | Da de baja una entrada y refresca de inmediato la caché en RAM (`204 No Content`). |

---

## Interfaz de Usuario (Frontend React)

Se implementó la vista de administración en `frontend/src/pages/ListsPage.tsx`:
- **Pestañas dedicadas:** Separación clara entre *Lista de Bloqueo (BLOCK)* y *Lista de Confianza (ALLOW)* con insignias semánticas.
- **KPIs en tiempo real:** Contadores instantáneos de elementos bloqueados y elementos confiables.
- **Filtrado dinámico:** Filtro rápido por tipo de entidad (`Dispositivo`, `Dirección IP`, `Correo electrónico`, `Huella de tarjeta`).
- **Modal de creación:** Formulario asistido con validaciones de tipo, selección de fecha de expiración opcional y motivo mínimo.
- **Eliminación con confirmación:** Acción directa para remover entradas obsoletas y liberar el tráfico en tiempo real.
- **Cero CSS inline:** Diseñado siguiendo los estándares de diseño sobrio en `frontend/src/styles/lists.css`.

---

## Archivos Creados o Modificados

### Backend
- `backend/src/main/resources/db/migration/V12__blocklist_allowlist_schema.sql` (Migración Flyway).
- `backend/src/main/java/com/pillapago/lists/domain/ListType.java` (Enum: BLOCK, ALLOW).
- `backend/src/main/java/com/pillapago/lists/domain/ListEntryType.java` (Enum: DEVICE, IP, EMAIL, CARD_FINGERPRINT).
- `backend/src/main/java/com/pillapago/lists/domain/ListEntryEntity.java` (Entidad JPA multi-tenant).
- `backend/src/main/java/com/pillapago/lists/repository/ListEntryRepository.java` (Repositorio Spring Data JPA).
- `backend/src/main/java/com/pillapago/lists/dto/CreateListEntryRequest.java` (DTO de entrada validado).
- `backend/src/main/java/com/pillapago/lists/dto/ListEntryResponseDto.java` (DTO de respuesta con máscaras).
- `backend/src/main/java/com/pillapago/lists/exception/ListEntryAlreadyExistsException.java` (Excepción de unicidad).
- `backend/src/main/java/com/pillapago/lists/service/ListCacheService.java` (Gestor de caché in-memory $O(1)$).
- `backend/src/main/java/com/pillapago/lists/service/ListService.java` (Lógica de negocio, cifrado y auditoría).
- `backend/src/main/java/com/pillapago/lists/controller/ListEntryController.java` (Controlador REST con RBAC).
- `backend/src/main/java/com/pillapago/evaluation/service/TransactionEvaluationService.java` (Short-circuit en Hot Path).
- `backend/src/main/java/com/pillapago/shared/exception/GlobalExceptionHandler.java` (Mapeo de conflicto 409).
- `backend/src/test/java/com/pillapago/lists/ListManagementE2ETest.java` (Suite de pruebas E2E con reporte físico).

### Frontend
- `frontend/src/api/lists.ts` (Cliente API REST tipado).
- `frontend/src/styles/lists.css` (Estilos corporativos modulares).
- `frontend/src/pages/ListsPage.tsx` (Componente de vista principal).
- `frontend/src/App.tsx` (Ruta `/lists` con protección de rol).

### Diagramas y Documentación
- `docs/diagrams/hu13_list_management_lifecycle_e2e.svg` (Diagrama de secuencia UML del ciclo de vida).
- `docs/hu13_listas_bloqueo_confianza_resumen.md` (Este documento).
- `backend/target/reporte_hu13_listas_bloqueo_confianza.txt` (Reporte de ejecución de pruebas de aceptación).
