# Pillapago — Motor de decisión antifraude en tiempo real para comercios online

> Documento de especificación v0.3 · Estado: borrador para validar antes de codificar
> Stack base: Java 21 · Spring Boot 3.x · PostgreSQL (Neon) · Redis · Podman (local) · Floci (emulador AWS, solo local) · Render + Vercel (nube gratuita) · React + TypeScript

---

## Tabla de contenidos
1. [Título comercial](#1-título-comercial)
2. [Contexto y problema de origen](#2-contexto-y-problema-de-origen)
3. [Propuesta de valor](#3-propuesta-de-valor)
4. [Glosario](#4-glosario)
5. [Alcance](#5-alcance)
6. [Actores, roles y RBAC](#6-actores-roles-y-rbac)
7. [Requisitos funcionales](#7-requisitos-funcionales)
8. [Requisitos no funcionales](#8-requisitos-no-funcionales)
9. [Historias de usuario](#9-historias-de-usuario)
10. [Modelo de datos y estados](#10-modelo-de-datos-y-estados)
11. [Contrato de la API](#11-contrato-de-la-api)
12. [Arquitectura](#12-arquitectura)
13. [Seguridad y protección de datos](#13-seguridad-y-protección-de-datos)
14. [Estructura de carpetas](#14-estructura-de-carpetas)
15. [Stack tecnológico](#15-stack-tecnológico)
16. [Entornos, despliegue y CI/CD](#16-entornos-despliegue-y-cicd)
17. [Estrategia de pruebas](#17-estrategia-de-pruebas)
18. [Riesgos y supuestos](#18-riesgos-y-supuestos)
19. [Hoja de ruta por fases](#19-hoja-de-ruta-por-fases)
20. [Historial de versiones](#20-historial-de-versiones)

---

## 1. Título comercial

**Nombre del producto:** **Pillapago**
**Subtítulo:** *Motor de decisión antifraude en tiempo real para comercios online*
**Eslogan sugerido:** *Decide en milisegundos si un pago es de fiar.*

**Por qué este nombre:** "pillar" es jerga común en Perú y España para atrapar o darse cuenta de algo. Pillapago es el sistema que *pilla* el pago sospechoso: dice lo que hace, es juvenil, se pronuncia fácil (pi-ya-PA-go) y es propio del mercado objetivo (Perú/Latam). No lleva "API" ni "Enterprise" porque el nombre de un producto no debe prometer más de lo que entrega.

**Alternativas evaluadas:** `Centinela` (descartado por gusto y por ser una palabra muy común), `Cazapago`, `Ojo al Pago`.
**Pendiente antes de fijarlo:** una búsqueda web no encontró ningún producto con el nombre exacto, pero falta comprobar GitHub, dominios (`.dev` / `.com`) e INDECOPI. El nombre es informal; si el producto se orientara a empresas grandes, convendría reevaluarlo.

---

## 2. Contexto y problema de origen

### Escenario base
**ElectroPerú** es una tienda online peruana que vende celulares y electrónica. Cobra con tarjeta y billeteras digitales. Procesa unos 3,000 pedidos al día.

### Qué hace hoy a medias
- Una persona revisa a mano los pedidos "que se ven raros" (sin criterio uniforme, sin registro del porqué).
- Cuando alguien reclama el cargo a su banco (**contracargo**), la tienda pierde la mercadería, el dinero y paga una comisión por disputa.
- Las herramientas antifraude del mercado están pensadas para empresas grandes: son caras, opacas ("el sistema dijo que no") y difíciles de ajustar por un analista no técnico.
- Las reglas que existen están escritas dentro del código de la tienda: cambiar un umbral exige que un programador despliegue una nueva versión.

### Problema que resuelve Pillapago
> Los comercios medianos no tienen una forma **rápida, configurable y explicable** de decidir, en el momento del pago, si una transacción debe aprobarse, revisarse o rechazarse.

### Lo que NO es el problema
Pillapago **no determina con certeza qué es fraude** (eso solo se confirma semanas después, con un contracargo). Pillapago **estima riesgo** con señales y actúa según reglas que define el negocio.

---

## 3. Propuesta de valor

| Para quién | Valor |
|---|---|
| **Comercio** | Reduce pérdidas por contracargos sin rechazar clientes buenos; decide en milisegundos sin frenar el pago. |
| **Analista de riesgo** | Cambia reglas y umbrales desde un panel, sin pedir un despliegue a sistemas. Ve por qué se tomó cada decisión. |
| **Gerencia / auditoría** | Trazabilidad completa: quién cambió qué regla, qué versión decidió cada transacción, cuántas pérdidas se evitaron. |

**Diferenciales técnicos:**
- Reglas configurables, **versionadas** y publicables sin redesplegar.
- **Explicabilidad:** cada decisión guarda los motivos y la versión de reglas que la produjo.
- Latencia objetivo de decisión en el orden de **decenas de milisegundos**.
- Ciclo de retroalimentación: los contracargos confirmados miden qué reglas aciertan.
- **Privacidad por diseño:** datos personales cifrados a nivel de campo, enmascarados por defecto y con cada revelación auditada (sección 13).

---

## 4. Glosario

| Término | Significado |
|---|---|
| **Transacción** | Intento de pago enviado por el comercio para ser evaluado. |
| **Decisión** | Resultado: `APPROVE`, `REVIEW` o `REJECT`. |
| **Score de riesgo** | Suma de puntos de las reglas activadas (0–100). |
| **Regla** | Condición + puntos. Ej.: "dispositivo en más de 3 cuentas en 24 h → +40". |
| **Velocity (velocidad)** | Conteo de eventos en una ventana de tiempo (ej. compras en 10 min). |
| **Conjunto de reglas (rule set)** | Versión publicada e inmutable de todas las reglas activas de un comercio. |
| **Contracargo (chargeback)** | Reclamo del titular de la tarjeta al banco; confirma un fraude. |
| **Caso** | Transacción en `REVIEW` que espera la resolución de un analista. |
| **Tenant** | Cada comercio cliente; sus datos están aislados de los demás. |
| **Idempotencia** | Reenviar la misma petición produce el mismo resultado sin duplicar efectos. |
| **Fail-open / fail-closed** | Qué decisión por defecto se toma si el motor falla o tarda demasiado. |
| **Cifrado a nivel de campo** | Cifrar cada dato personal dentro de la base de datos, de modo que un volcado de la tabla no lo revele. |
| **Cifrado de sobre (envelope encryption)** | Los datos se cifran con una clave de datos, y esa clave se protege con una clave maestra guardada en KMS. |
| **Índice ciego** | Huella HMAC de un dato que permite buscar por igualdad exacta sin descifrarlo. |
| **Enmascarado** | Mostrar solo parte de un dato (ej. `190.xxx.xxx.12`) hasta que un usuario autorizado lo revele. |
| **MFA / TOTP** | Autenticación con un segundo factor: código de 6 dígitos que cambia cada 30 s en una app autenticadora. |
| **Cripto-eliminación** | Eliminar la clave de cifrado para que los datos cifrados con ella queden ilegibles para siempre. |
| **Puerto y adaptador** | Interfaz del negocio (puerto) con implementaciones intercambiables (adaptadores). Ej.: `ColaPort` tiene un adaptador SQS (local) y uno Postgres (nube). |
| **Outbox** | Tabla en la base de datos que guarda los eventos pendientes de procesar; un proceso los toma con reintentos. Hace de cola sin servicio extra. |
| **Rootless** | Contenedor ejecutado por un usuario normal, sin privilegios de administrador en el equipo. |

---

## 5. Alcance

### Dentro del alcance (MVP)
- Registro de comercios, usuarios, roles y claves de API.
- Endpoint de evaluación de transacciones con decisión, score y motivos.
- Motor de reglas configurable (condiciones simples + velocity) con versionado.
- Listas de bloqueo y de confianza.
- Cola de revisión manual para analistas.
- Registro de contracargos y métricas de desempeño por regla.
- Dashboard de métricas y auditoría de cambios.
- Simulador de tráfico para demostración y pruebas de carga.
- Protección de datos personales: cifrado en tránsito y en reposo, cifrado a nivel de campo, enmascarado y auditoría de accesos.
- Autenticación multifactor para los roles con acceso a datos sensibles.

### Fuera del alcance
- Procesar pagos o conectarse a pasarelas/bancos reales.
- Recibir o almacenar números de tarjeta (PAN). El comercio envía un identificador ya anonimizado.
- Modelos de machine learning (se evaluarán en la fase 2).
- Aplicación móvil.
- Panel de super-administrador de la plataforma (multi-tenant se gestiona por alta de comercios).
- Cumplimiento certificado PCI-DSS (se diseña para minimizar su alcance, sin certificarlo).
- Certificación o dictamen legal de cumplimiento normativo (se diseña alineado con la Ley 29733, sin certificarlo).

### Fases
- **MVP:** lo marcado "MVP" en los requisitos.
- **Fase 2:** simulación de reglas contra histórico, webhooks, carga CSV de contracargos, reportes CSV, sugerencias basadas en ML.
- **Corte mínimo recomendado (MVP-núcleo):** HU-01, HU-02, HU-06, HU-07, HU-08, HU-09 y enmascarado básico (RF-23 sin MFA). MFA (RF-22), auditoría encadenada (RNF-35) y rotación de claves (RNF-39) se construyen después de que el núcleo funcione, sin cambiar el diseño.

---

## 6. Actores, roles y RBAC

**RBAC (Role-Based Access Control):** los permisos no se asignan a cada persona, sino a un **rol**; cada usuario tiene un rol y hereda sus permisos. Así se controla quién puede ver o cambiar qué sin configurar usuario por usuario.

### Actores
| Actor | Tipo | Descripción |
|---|---|---|
| **MERCHANT_ADMIN** | Humano | Administra el comercio: usuarios, claves de API, configuración. |
| **RISK_ANALYST** | Humano | Define reglas, revisa casos, registra contracargos. |
| **AUDITOR** | Humano | Solo lectura: decisiones, métricas y auditoría. |
| **Sistema del comercio** | Máquina | Tienda online que consume la API de evaluación con una **API key**. |

### Matriz de permisos
| Acción | MERCHANT_ADMIN | RISK_ANALYST | AUDITOR | API key |
|---|:-:|:-:|:-:|:-:|
| Gestionar usuarios y roles | Sí | No | No | No |
| Gestionar API keys | Sí | No | No | No |
| Configurar umbrales y política de falla | Sí | Sí | No | No |
| Crear / editar reglas | No | Sí | No | No |
| Publicar conjunto de reglas | Sí | Sí | No | No |
| Gestionar listas de bloqueo/confianza | No | Sí | No | No |
| Resolver casos en revisión | No | Sí | No | No |
| Registrar contracargos | No | Sí | No | No |
| Ver decisiones, dashboard y auditoría | Sí | Sí | Sí | No |
| Ver datos sensibles enmascarados | Sí | Sí | Sí | No |
| Revelar datos sensibles completos (con justificación y MFA) | No | Sí | Sí | No |
| Gestionar su propia autenticación multifactor | Sí | Sí | Sí | No |
| Evaluar transacciones | No | No | No | Sí |

> Aislamiento multi-tenant: toda consulta se filtra por `merchant_id` tomado del token o de la API key, **nunca** de un parámetro enviado por el cliente.
>
> Mínimo privilegio: el MERCHANT_ADMIN administra el comercio pero no necesita ver los datos personales de las transacciones, por eso no puede revelarlos.

---

## 7. Requisitos funcionales

> Los requisitos funcionales describen **qué hace** el sistema.

### Identidad y acceso
| ID | Requisito | Rol | Fase |
|---|---|---|---|
| RF-01 | Registrar un comercio y su usuario administrador inicial. | Público | MVP |
| RF-02 | Verificar el correo con un código numérico de 6 dígitos. | Público | MVP |
| RF-03 | Iniciar sesión con JWT de acceso y refresh token rotativo; cerrar sesión. | Todos | MVP |
| RF-04 | Recuperar contraseña mediante enlace/código enviado al correo. | Público | MVP |
| RF-05 | Invitar usuarios, asignar o cambiar rol y desactivar usuarios. | MERCHANT_ADMIN | MVP |
| RF-06 | Crear, listar, rotar y revocar API keys. | MERCHANT_ADMIN | MVP |

### Evaluación
| ID | Requisito | Rol | Fase |
|---|---|---|---|
| RF-07 | Evaluar una transacción y devolver decisión, score y motivos. | API key | MVP |
| RF-08 | Garantizar idempotencia por identificador de transacción. | API key | MVP |
| RF-09 | Administrar listas de bloqueo y de confianza (dispositivo, IP, correo hasheado, huella de tarjeta). | RISK_ANALYST | MVP |

### Reglas
| ID | Requisito | Rol | Fase |
|---|---|---|---|
| RF-10 | Crear, editar, activar y desactivar reglas (condiciones, velocity y puntos). | RISK_ANALYST | MVP |
| RF-11 | Publicar un conjunto de reglas como versión inmutable y poder volver a una versión anterior. | ADMIN / ANALYST | MVP |
| RF-12 | Configurar umbrales (aprobar/revisar/rechazar) y política ante falla (fail-open / fail-closed). | ADMIN / ANALYST | MVP |
| RF-13 | Simular una regla contra transacciones históricas antes de publicarla. | RISK_ANALYST | Fase 2 |

### Casos y retroalimentación
| ID | Requisito | Rol | Fase |
|---|---|---|---|
| RF-14 | Listar, filtrar, asignar y resolver casos en revisión (aprobar/rechazar con comentario). | RISK_ANALYST | MVP |
| RF-15 | Consultar el detalle de una decisión y su explicación (reglas activadas, versión, datos usados). | Todos los humanos | MVP |
| RF-16 | Registrar un contracargo sobre una transacción (individual en MVP; carga CSV en fase 2). | RISK_ANALYST | MVP / F2 |

### Analítica, integración y trazabilidad
| ID | Requisito | Rol | Fase |
|---|---|---|---|
| RF-17 | Dashboard: volumen por decisión, latencia, tasa de contracargo y rendimiento por regla. | Todos los humanos | MVP |
| RF-18 | Generar reportes CSV de forma asíncrona y descargarlos con URL prefirmada. | ANALYST / AUDITOR | Fase 2 |
| RF-19 | Notificar al comercio por webhook firmado (HMAC) cuando se resuelve un caso, con reintentos. | Sistema | Fase 2 |
| RF-20 | Registrar auditoría de acciones sensibles (cambios de reglas, roles, claves, resoluciones). | ADMIN / AUDITOR | MVP |
| RF-21 | Simulador de tráfico que genere transacciones normales y sospechosas contra la API. | Desarrollo / demo | MVP |

### Seguridad y privacidad de datos
| ID | Requisito | Rol | Fase |
|---|---|---|---|
| RF-22 | Autenticación multifactor (TOTP) con códigos de recuperación; obligatoria para los roles con acceso a datos sensibles. | Todos los humanos | MVP |
| RF-23 | Mostrar los datos sensibles enmascarados por defecto y permitir revelarlos con justificación, registrando cada acceso. | ANALYST / AUDITOR | MVP |
| RF-24 | Atender solicitudes de acceso y eliminación de datos personales de un cliente final (derechos ARCO), localizando sus registros por índice ciego. | MERCHANT_ADMIN | Fase 2 |
| RF-25 | Purgar o anonimizar automáticamente los datos personales que superen la política de retención del comercio. | Sistema | Fase 2 |

---

## 8. Requisitos no funcionales

> Los no funcionales describen **qué tan bien** lo hace: rendimiento, seguridad, usabilidad, compatibilidad, etc. Cada uno es medible y tiene condición.

### Rendimiento y capacidad
| ID | Requisito | Métrica |
|---|---|---|
| RNF-01 | Latencia del endpoint de evaluación | p95 < 100 ms y p99 < 250 ms con 100 TPS sostenidos y 10 reglas activas, **medido en local** con el hardware documentado en el README. La demo en Render gratis (0.1 CPU) no garantiza este objetivo (R3). |
| RNF-02 | Latencia de lecturas del dashboard | p95 < 500 ms con 1 millón de transacciones almacenadas. |
| RNF-03 | Concurrencia | 200 peticiones concurrentes sin errores ni degradar RNF-01. |
| RNF-04 | Consumo de recursos | Contenedor de la API ≤ 512 MB de RAM total (heap ≈ 256–300 MB, `-XX:+UseSerialGC`), para caber en la instancia gratuita de Render. |
| RNF-05 | Escalabilidad | API *stateless*; escalable horizontalmente sin cambios de código. |

### Fiabilidad
| ID | Requisito | Métrica |
|---|---|---|
| RNF-06 | Tolerancia a fallas del motor | Si la evaluación supera 300 ms o falla una dependencia, se aplica la política configurada (fail-open / fail-closed) y se registra el incidente. |
| RNF-07 | Disponibilidad | Objetivo de demostración: 99 % mensual (*best effort*, infraestructura gratuita). No se promete 99.9 %. La instancia gratuita se apaga tras 15 min sin tráfico y tarda cerca de 1 min en despertar; se acepta para la demo (R11). |
| RNF-08 | Integridad de datos | Las decisiones son inmutables; no hay duplicados por `(merchant_id, transaction_id)`. |
| RNF-09 | Procesos asíncronos | Ningún evento se pierde silenciosamente: reintentos y estado `FAILED` / cola de mensajes fallidos (DLQ). |

### Seguridad y privacidad
| ID | Requisito | Métrica |
|---|---|---|
| RNF-10 | Contraseñas | BCrypt con costo ≥ 12; política mínima de 10 caracteres. |
| RNF-11 | Autenticación | JWT de acceso de 15 min; refresh token rotativo con detección de reutilización. |
| RNF-12 | API keys | Se muestran una sola vez; en BD solo se guarda su hash (SHA-256). |
| RNF-13 | Autorización | RBAC aplicado en el servidor en cada endpoint; aislamiento por tenant verificado con pruebas automatizadas. |
| RNF-14 | Transporte y secretos | TLS en todo tráfico externo; secretos en variables de entorno (Render en la nube, `.env` en local), nunca en el repositorio. |
| RNF-15 | Datos sensibles | No se recibe ni almacena PAN; correo e identificadores se guardan hasheados cuando la regla lo permite. |
| RNF-16 | Protección de datos personales | Alineado con la Ley 29733 (Perú): minimización, finalidad y política de retención configurable (por defecto 13 meses). |
| RNF-17 | Abuso | *Rate limiting* por API key y por IP en login y recuperación de contraseña. |
| RNF-18 | Confidencialidad entre comercios | Ningún dato, regla o métrica de un tenant es visible para otro. |

### Usabilidad, compatibilidad y accesibilidad (dashboard)
| ID | Requisito | Métrica |
|---|---|---|
| RNF-19 | Usabilidad | Un analista resuelve un caso en ≤ 3 clics desde la cola; los mensajes de error indican qué corregir. |
| RNF-20 | Compatibilidad de navegadores | Últimas 2 versiones de Chrome, Edge, Firefox y Safari. |
| RNF-21 | Responsive | Funcional desde 360 px de ancho. |
| RNF-22 | Accesibilidad | WCAG 2.1 nivel AA: navegación por teclado, etiquetas ARIA, contraste ≥ 4.5:1. |
| RNF-23 | Idioma | Interfaz en español (es-PE); textos externalizados para añadir otros idiomas. |

### Mantenibilidad y operación
| ID | Requisito | Métrica |
|---|---|---|
| RNF-24 | Calidad de código | Cobertura ≥ 80 % en capas de servicio y motor de reglas (JaCoCo); reglas de arquitectura verificadas con ArchUnit. |
| RNF-25 | Observabilidad | Logs estructurados con *correlation id*, métricas con Micrometer y *health checks*. |
| RNF-26 | Documentación de la API | OpenAPI/Swagger siempre actualizado; versionado de rutas (`/v1`). |
| RNF-27 | Errores | Respuestas de error bajo RFC 7807 (`ProblemDetail`). |
| RNF-28 | Portabilidad | Todo el sistema local levanta con `podman compose up`; la nube se despliega desde el mismo Dockerfile. |

### Protección de datos y cifrado
| ID | Requisito | Métrica |
|---|---|---|
| RNF-29 | Cifrado en tránsito | TLS 1.2 como mínimo (1.3 preferido) en todo canal externo; conexión a la base de datos con verificación de certificado (`sslmode=verify-full`); HSTS activo; Redis solo accesible desde la red interna (en local, publicado únicamente en 127.0.0.1) y con contraseña. |
| RNF-30 | Cifrado en reposo | Almacenamiento gestionado por Neon y Render (cifrado según su documentación, a verificar); el cifrado de campo (RNF-31) no depende de ello; objetos de la fase 2 con cifrado del proveedor; backups cifrados; Redis sin persistencia en disco. |
| RNF-31 | Cifrado a nivel de campo | Los datos personales (IP, `deviceId`, `customerId`, `cardFingerprint`, valores de listas) se guardan con AES-256-GCM mediante cifrado de sobre (KMS + clave de datos por comercio). Una consulta directa a la base de datos solo devuelve texto cifrado. |
| RNF-32 | Índices ciegos y seudonimización | Las búsquedas por identificador usan HMAC-SHA256 con clave por comercio; Redis solo contiene identificadores seudonimizados, nunca valores en claro. |
| RNF-33 | Enmascarado y revelado | La interfaz muestra los datos personales enmascarados; revelarlos exige rol autorizado, MFA verificada en los últimos 15 min y justificación, y queda auditado. Más de 20 revelaciones por hora por usuario generan una alerta. |
| RNF-34 | Logs sin datos personales | Filtro de redacción en logs, trazas, métricas y mensajes de error; pruebas automáticas que fallan si aparece un dato personal. |
| RNF-35 | Auditoría a prueba de manipulación | `audit_log` de solo inserción con encadenado de hashes; una verificación diaria detecta cualquier alteración. Incluye accesos a datos revelados. |
| RNF-36 | Mínimo privilegio | La aplicación solo accede a los recursos que necesita (su base de datos, su clave maestra y su almacenamiento de objetos); el usuario de base de datos de la aplicación no tiene permisos de DDL; sin acceso de desarrolladores a producción salvo acceso de emergencia registrado. |
| RNF-37 | Sesión del dashboard | Token de acceso solo en memoria; refresh token en cookie HttpOnly, Secure y SameSite=Strict; CSP estricta; cierre de sesión por inactividad a los 15 min; `Cache-Control: no-store` en respuestas con datos sensibles. |
| RNF-38 | Identificadores opacos | Las rutas usan UUID no secuenciales para impedir la enumeración de recursos (IDOR). |
| RNF-39 | Gestión y rotación de claves | Rotación anual de la clave maestra; versión de clave por registro; cripto-eliminación al dar de baja un comercio. |
| RNF-40 | Aislamiento del entorno local | Contenedores *rootless* con Podman; puertos publicados solo en `127.0.0.1`; ningún contenedor monta el socket del motor de contenedores; imágenes con versión fijada (no `latest`) una vez validadas. |

---

## 9. Historias de usuario

Formato: **Como** [rol] **quiero** [acción] **para** [beneficio]. Cada historia indica entrada, salida, flujo, flujos alternos, criterios de aceptación y requisitos relacionados.

---

### HU-01 — Registrar mi comercio
**Como** responsable de un comercio **quiero** registrarme **para** empezar a proteger mis ventas.

- **Entrada:** nombre del comercio, nombre y correo del administrador, contraseña, confirmación de contraseña.
- **Salida (éxito):** `201 Created`; mensaje "Cuenta creada. Revisa tu correo para verificarla"; se envía un código de 6 dígitos; redirección a la pantalla de verificación.
- **Salida (error):** `400` con detalle por campo (correo inválido, contraseña débil) o `409` si el correo ya existe.

**Flujo principal**
1. El usuario hace clic en "Registrarse".
2. El sistema muestra el formulario de registro.
3. El usuario completa los campos y hace clic en "Crear cuenta".
4. El sistema valida los datos, guarda el comercio y el usuario (rol `MERCHANT_ADMIN`, estado `PENDING_VERIFICATION`) con la contraseña hasheada.
5. El sistema envía el código de verificación al correo.
6. El sistema muestra la notificación y redirige a "Verificar correo".
7. El usuario ingresa el código; el sistema activa la cuenta y redirige al login.

**Flujos alternos**
- Código incorrecto → mensaje de error; máximo 5 intentos y luego bloqueo temporal.
- Código vencido (15 min) → botón "Reenviar código".

**Criterios de aceptación**
- La contraseña nunca se guarda en texto plano.
- No se puede iniciar sesión sin verificar el correo.
- No pueden coexistir dos usuarios con el mismo correo.

**Relacionados:** RF-01, RF-02 · RNF-10, RNF-17, RNF-19

---

### HU-02 — Iniciar y cerrar sesión
**Como** usuario registrado **quiero** iniciar sesión **para** acceder a mi panel según mi rol.

- **Entrada:** correo y contraseña.
- **Salida (éxito):** `200`; token de acceso (15 min) y refresh token; redirección al dashboard.
- **Salida (error):** `401` con mensaje genérico ("Credenciales inválidas"), sin revelar si el correo existe.

**Flujo principal**
1. El usuario abre el login e ingresa sus credenciales.
2. El sistema valida las credenciales y que la cuenta esté verificada y activa.
3. El sistema emite los tokens y registra el acceso.
4. El sistema redirige al dashboard y muestra solo las opciones del rol.

**Flujos alternos**
- Cuenta sin verificar → se ofrece reenviar el código.
- 5 intentos fallidos → bloqueo temporal de 15 min.
- Token vencido → el cliente usa el refresh; si el refresh fue reutilizado, se revoca toda la sesión.
- Usuario con MFA activa → tras validar la contraseña, el sistema pide el código de 6 dígitos antes de emitir los tokens (ver HU-17).

**Criterios de aceptación:** el refresh token rota en cada uso; "cerrar sesión" invalida el refresh.
**Relacionados:** RF-03, RF-22 · RNF-10, RNF-11, RNF-17

---

### HU-03 — Recuperar mi contraseña
**Como** usuario **quiero** restablecer mi contraseña **para** recuperar el acceso si la olvido.

- **Entrada:** correo; luego código/enlace y nueva contraseña.
- **Salida:** siempre `200` con "Si el correo existe, enviaremos instrucciones" (evita revelar cuentas); tras el restablecimiento, redirección al login y revocación de sesiones previas.

**Flujo principal**
1. El usuario hace clic en "Olvidé mi contraseña" e ingresa su correo.
2. El sistema envía un enlace de un solo uso (vence en 30 min) si la cuenta existe.
3. El usuario abre el enlace y define la nueva contraseña.
4. El sistema actualiza el hash, revoca todos los refresh tokens y muestra "Contraseña actualizada".

**Criterios de aceptación:** el enlace solo funciona una vez; rate limiting de solicitudes.
**Relacionados:** RF-04 · RNF-10, RNF-17

---

### HU-04 — Invitar a un analista y asignarle un rol
**Como** MERCHANT_ADMIN **quiero** invitar usuarios y asignarles un rol **para** que cada persona haga solo lo que le corresponde.

- **Entrada:** correo, nombre, rol (`RISK_ANALYST` o `AUDITOR`).
- **Salida:** `201`; se envía una invitación con enlace para definir contraseña; el usuario aparece como "Invitado".
- **Error:** `403` si quien invita no es administrador; `409` si el correo ya pertenece al comercio.

**Flujo principal**
1. El admin abre "Usuarios" → "Invitar".
2. Completa el formulario y confirma.
3. El sistema crea el usuario con estado `INVITED` y envía la invitación.
4. El invitado define su contraseña y queda activo con el rol asignado.

**Criterios de aceptación:** el cambio de rol queda en auditoría; un admin no puede quitarse a sí mismo el último rol de administrador.
**Relacionados:** RF-05, RF-20 · RNF-13, RNF-18

---

### HU-05 — Generar una API key
**Como** MERCHANT_ADMIN **quiero** generar una API key **para** que mi tienda pueda consultar a Pillapago.

- **Entrada:** nombre descriptivo (ej. "Tienda web producción").
- **Salida:** `201`; la clave completa **se muestra una única vez**; en la lista solo se ve el prefijo y la fecha.

**Flujo principal**
1. El admin abre "Integración" → "Nueva API key".
2. Ingresa un nombre y confirma.
3. El sistema genera la clave aleatoria, guarda solo su hash y la muestra con aviso "cópiala ahora".
4. El admin puede después revocarla o rotarla.

**Criterios de aceptación:** una clave revocada deja de funcionar de inmediato (≤ 1 min por la caché).
**Relacionados:** RF-06, RF-20 · RNF-12, RNF-14

---

### HU-06 — Evaluar una transacción *(historia central)*
**Como** sistema del comercio **quiero** enviar una transacción y recibir una decisión **para** aprobarla, revisarla o rechazarla antes de cobrar.

- **Entrada:** `transaction_id` del comercio, monto y moneda, `customer_id`, correo (o hash), `device_id`, IP, `card_fingerprint`, país de la tarjeta, país de envío, fecha/hora, `Idempotency-Key` opcional.
- **Salida (éxito):** `200` con `decision`, `risk_score`, lista de `reasons`, `rule_set_version`, `latency_ms`.
- **Salida (error):** `400` datos inválidos; `401` API key inválida; `429` límite excedido.

**Flujo principal**
1. La tienda envía `POST /v1/transactions/evaluate` con su API key.
2. El sistema autentica la clave y resuelve el comercio.
3. El sistema revisa idempotencia: si el `transaction_id` ya fue evaluado, devuelve la misma respuesta.
4. El sistema revisa listas de bloqueo/confianza (una coincidencia en bloqueo → `REJECT` inmediato).
5. El sistema calcula las métricas de *velocity* (ej. compras de este dispositivo en 10 min) consultando Redis.
6. El motor evalúa las reglas de la versión activa y suma los puntos.
7. El sistema compara el score con los umbrales y obtiene la decisión.
8. El sistema responde a la tienda.
9. **Después de responder** (en segundo plano): cifra los campos personales, guarda la decisión y los motivos, y si es `REVIEW` crea un caso para el analista.

**Flujos alternos**
- Evaluación > 300 ms o falla de Redis → se aplica la política del comercio (fail-open: `APPROVE` marcado como "degradado"; fail-closed: `REVIEW`) y se registra el incidente.
- Mismo `transaction_id` con datos distintos → `409`.

**Criterios de aceptación**
- Reenviar la misma petición 10 veces genera una sola decisión guardada.
- La respuesta cumple RNF-01.
- Cada decisión guarda la versión de reglas que la produjo.

**Relacionados:** RF-07, RF-08, RF-09, RF-12 · RNF-01, RNF-03, RNF-06, RNF-08, RNF-15

---

### HU-07 — Crear una regla
**Como** RISK_ANALYST **quiero** crear reglas con condiciones y puntos **para** ajustar la detección sin pedir un despliegue.

- **Entrada:** nombre, descripción, condiciones (campo + operador + valor, o métrica de velocity + ventana + umbral), puntos (0–100), estado activa/inactiva.
- **Salida:** `201`; la regla queda en **borrador** (no afecta decisiones hasta publicar).
- **Error:** `400` si la condición es inválida (campo inexistente, operador incompatible).

**Ejemplo de regla:** *"Si `device_accounts_24h` > 3 → +40 puntos"*.

**Flujo principal**
1. El analista abre "Reglas" → "Nueva regla".
2. Elige campo, operador y valor en un formulario guiado (sin escribir código).
3. Define los puntos y guarda.
4. El sistema valida la regla, la guarda como borrador y registra la auditoría.

**Criterios de aceptación:** las condiciones se evalúan con un lenguaje propio y acotado (sin ejecutar código arbitrario).
**Relacionados:** RF-10, RF-20 · RNF-13, RNF-19

---

### HU-08 — Publicar una versión de reglas
**Como** RISK_ANALYST **quiero** publicar el conjunto de reglas como una versión **para** que entren en vigor de forma controlada y poder revertir.

- **Entrada:** confirmación de publicación y nota de cambio.
- **Salida:** `200`; nueva versión `vN` activa; la anterior queda archivada.

**Flujo principal**
1. El analista revisa el resumen de cambios (reglas nuevas, editadas, desactivadas).
2. Hace clic en "Publicar".
3. El sistema genera la versión inmutable, la activa de forma atómica y avisa a todas las instancias para recargar su caché.
4. Muestra "Versión v12 publicada".

**Flujo alterno:** "Volver a vN-1" reactiva una versión anterior (también queda en auditoría).
**Criterios de aceptación:** una transacción nunca se evalúa con un conjunto de reglas a medio publicar.
**Relacionados:** RF-11, RF-20 · RNF-08

---

### HU-09 — Resolver un caso en revisión
**Como** RISK_ANALYST **quiero** revisar las transacciones dudosas **para** aprobar las legítimas y rechazar las fraudulentas.

- **Entrada:** decisión (`aprobar` / `rechazar`) y comentario obligatorio.
- **Salida:** el caso pasa a resuelto; queda registrado quién resolvió y cuándo; (fase 2) se notifica al comercio por webhook.

**Flujo principal**
1. El analista abre la cola de casos y filtra por antigüedad o monto.
2. Toma un caso (queda asignado a él) y ve el detalle: datos personales enmascarados (se revelan solo mediante HU-16), score, reglas activadas, historial del cliente/dispositivo.
3. Decide aprobar o rechazar y escribe el comentario.
4. El sistema guarda la resolución y la auditoría.

**Criterios de aceptación:** dos analistas no pueden resolver el mismo caso a la vez (bloqueo optimista); un caso resuelto no se puede editar.
**Relacionados:** RF-14, RF-15, RF-19, RF-20 · RNF-08, RNF-19

---

### HU-10 — Registrar un contracargo
**Como** RISK_ANALYST **quiero** marcar una transacción como fraude confirmado **para** medir qué reglas aciertan.

- **Entrada:** `transaction_id`, fecha del contracargo, motivo (código del banco), monto.
- **Salida:** `201`; la transacción queda marcada como `CONFIRMED_FRAUD`.
- **Error:** `404` si no existe; `409` si ya tiene contracargo.

**Flujo principal**
1. El analista busca la transacción.
2. Elige "Registrar contracargo" y completa el formulario.
3. El sistema marca el fraude y actualiza las métricas de efectividad de las reglas que intervinieron.

**Relacionados:** RF-16, RF-17, RF-20

---

### HU-11 — Ver por qué se tomó una decisión
**Como** AUDITOR **quiero** ver la explicación de una decisión **para** justificarla ante un reclamo.

- **Entrada:** `transaction_id`.
- **Salida:** datos evaluados (los personales, enmascarados), reglas activadas con sus puntos, versión de reglas, umbrales aplicados, resolución manual (si existió).

**Criterios de aceptación:** la explicación se reconstruye solo con lo guardado en ese momento (no cambia aunque cambien las reglas después).
**Relacionados:** RF-15, RF-20 · RNF-08, RNF-18

---

### HU-12 — Ver el dashboard
**Como** usuario del comercio **quiero** ver métricas **para** saber si el sistema está funcionando.

- **Entrada:** rango de fechas.
- **Salida:** volumen por decisión, % de revisión, latencia p50/p95/p99, tasa de contracargo, top de reglas por activaciones y por aciertos.

**Relacionados:** RF-17 · RNF-02, RNF-22

---

### HU-13 — Administrar listas de bloqueo y de confianza
**Como** RISK_ANALYST **quiero** bloquear o permitir un dispositivo, IP, correo o tarjeta **para** actuar de inmediato sobre casos conocidos.

- **Entrada:** tipo, valor, lista (`BLOCK` / `ALLOW`), motivo, vencimiento opcional.
- **Salida:** `201`; el valor se aplica en la siguiente evaluación.

**Relacionados:** RF-09, RF-20 · RNF-15

---

### HU-14 — Simular tráfico de prueba
**Como** desarrollador **quiero** generar miles de transacciones normales y sospechosas **para** demostrar el sistema y medir su rendimiento.

- **Entrada:** número de transacciones, tasa por segundo, proporción de fraudes simulados.
- **Salida:** transacciones enviadas a la API y un resumen (decisiones, latencias, errores).

**Relacionados:** RF-21 · RNF-01, RNF-03

---

### HU-15 — Descargar un reporte *(fase 2)*
**Como** AUDITOR **quiero** solicitar un reporte CSV **para** analizarlo fuera del sistema.

- **Entrada:** rango de fechas, filtros.
- **Salida:** `202 Accepted` con `report_id`; cuando termina, URL prefirmada de S3 válida 15 min.

**Flujo:** solicitar → el sistema encola el trabajo → un proceso en segundo plano genera el archivo → el usuario consulta el estado y descarga.
**Relacionados:** RF-18 · RNF-09

---

### HU-16 — Ver datos enmascarados y revelarlos con justificación
**Como** RISK_ANALYST o AUDITOR **quiero** ver los datos del cliente enmascarados y revelarlos solo cuando lo necesito **para** proteger la información personal y dejar constancia de quién la consultó.

- **Entrada:** `transaction_id`, campo a revelar, justificación (texto de al menos 10 caracteres).
- **Salida (éxito):** `200`; el valor completo se muestra durante 60 segundos y luego se vuelve a enmascarar; se registra en auditoría quién, cuándo, qué campo, de qué transacción y el motivo.
- **Salida (error):** `403` si el rol no está autorizado; `401` con solicitud de MFA si la última verificación tiene más de 15 minutos; `429` si se supera el límite de revelaciones.

**Flujo principal**
1. El usuario abre el detalle de una transacción y ve los datos enmascarados (`190.xxx.xxx.12`, `dev_****f3`).
2. Hace clic en "Revelar" junto al campo.
3. El sistema pide la justificación (y el código MFA si hace falta).
4. El usuario la escribe y confirma.
5. El sistema valida el rol, descifra solo ese campo, registra la auditoría y muestra el valor 60 segundos.
6. El valor vuelve a quedar enmascarado.

**Flujos alternos**
- Más de 20 revelaciones por hora por usuario → se bloquea y se notifica al MERCHANT_ADMIN.

**Criterios de aceptación**
- El valor revelado no se guarda en el navegador más allá de la vista ni aparece en logs.
- La auditoría registra el campo y el motivo, pero no el valor.
- El MERCHANT_ADMIN no puede revelar datos personales.

**Relacionados:** RF-20, RF-22, RF-23 · RNF-31, RNF-33, RNF-35, RNF-37

---

### HU-17 — Activar la autenticación multifactor
**Como** usuario con rol RISK_ANALYST, AUDITOR o MERCHANT_ADMIN **quiero** activar un segundo factor **para** proteger mi cuenta aunque se filtre mi contraseña.

- **Entrada:** escaneo del código QR en una app autenticadora y el código de 6 dígitos que genera.
- **Salida (éxito):** `200`; MFA activa y 10 códigos de recuperación mostrados una sola vez.
- **Salida (error):** `400` si el código es incorrecto o venció.

**Flujo principal**
1. El usuario abre "Mi perfil" y hace clic en "Activar MFA".
2. El sistema genera un secreto y muestra el QR.
3. El usuario lo escanea e ingresa el código de verificación.
4. El sistema valida el código, activa la MFA y muestra los códigos de recuperación.
5. En los siguientes inicios de sesión, tras la contraseña, el sistema pide el código.

**Flujos alternos**
- 5 códigos incorrectos seguidos → bloqueo temporal.
- Pérdida del dispositivo → se usa un código de recuperación o el MERCHANT_ADMIN reinicia la MFA (queda en auditoría).

**Criterios de aceptación**
- La MFA es obligatoria en el primer inicio de sesión tras la invitación para los roles que pueden revelar datos.
- El secreto TOTP se guarda cifrado y los códigos de recuperación, hasheados.

**Relacionados:** RF-22 · RNF-11, RNF-31

---

### HU-18 — Configurar umbrales y política de falla
**Como** MERCHANT_ADMIN o RISK_ANALYST **quiero** definir desde qué score se aprueba, revisa o rechaza y qué hacer si el motor falla **para** adaptar el nivel de riesgo a mi negocio.

- **Entrada:** umbral de revisión (0–100), umbral de rechazo (0–100, mayor que el de revisión), política de falla (`FAIL_OPEN` / `FAIL_CLOSED`).
- **Salida (éxito):** `200`; los nuevos valores rigen desde la siguiente publicación de reglas (HU-08); se muestra un resumen del cambio.
- **Salida (error):** `400` si el umbral de rechazo no es mayor que el de revisión; `403` si el rol no está autorizado.

**Flujo principal**
1. El usuario abre "Configuración" → "Umbrales".
2. El sistema muestra los valores actuales y cuántas transacciones de los últimos 7 días habrían cambiado de decisión (solo informativo, fase 2).
3. El usuario edita los valores y guarda.
4. El sistema valida, guarda como borrador y registra la auditoría.

**Criterios de aceptación:** el cambio no afecta decisiones hasta publicar; queda en auditoría quién cambió qué.
**Relacionados:** RF-12, RF-20 · RNF-06, RNF-13

---

### HU-19 — Consultar la auditoría
**Como** AUDITOR o MERCHANT_ADMIN **quiero** ver quién hizo qué y cuándo **para** investigar cambios y accesos sensibles.

- **Entrada:** rango de fechas; filtros opcionales por usuario, tipo de acción y entidad.
- **Salida:** lista paginada con fecha, usuario, acción, entidad y motivo (nunca el valor de un dato revelado); indicador de que la cadena de hashes es válida.

**Flujo principal**
1. El usuario abre "Auditoría" y elige el rango.
2. El sistema lista los eventos del comercio, del más reciente al más antiguo.
3. El usuario filtra y abre el detalle de un evento.

**Criterios de aceptación:** solo lectura; ningún usuario puede editar ni borrar eventos; no se muestran eventos de otros comercios.
**Relacionados:** RF-20 · RNF-13, RNF-18, RNF-35

---

## 10. Modelo de datos y estados

### Entidades principales
| Tabla | Propósito |
|---|---|
| `merchants` | Comercios (tenants) y su configuración (umbrales, política de falla). |
| `users` | Usuarios, rol y estado. |
| `refresh_tokens`, `verification_codes` | Sesiones y códigos de verificación/recuperación. |
| `api_keys` | Claves hasheadas por comercio. |
| `tenant_keys` | Claves de datos por comercio, cifradas con KMS y versionadas. |
| `mfa_credentials` | Secreto TOTP cifrado y códigos de recuperación hasheados. |
| `rule_sets`, `rules` | Versiones de reglas y reglas que las componen. |
| `list_entries` | Listas de bloqueo y de confianza (valores cifrados, con índice ciego). |
| `transactions` | Datos evaluados (campos personales cifrados, con índice ciego) y decisión (inmutable). |
| `decision_reasons` | Reglas activadas por decisión y sus puntos. |
| `review_cases` | Casos para analistas. |
| `chargebacks` | Contracargos confirmados. |
| `audit_log` | Registro de acciones sensibles, solo inserción, con encadenado de hashes. |
| `report_jobs` | Trabajos de reportes (fase 2). |

Índices clave: `transactions(merchant_id, created_at)`, único `(merchant_id, external_transaction_id)`, `review_cases(merchant_id, status)`, e índices ciegos (`device_hash`, `customer_hash`, `ip_hash`) para buscar sin descifrar.

### Estados
```
Decisión:   APPROVE | REVIEW | REJECT (inmutable)

Caso:       OPEN ──► ASSIGNED ──► RESOLVED_APPROVED
                          └─────► RESOLVED_REJECTED

Regla:      DRAFT ──► (publicada en un RuleSet) ──► ARCHIVED

Usuario:    INVITED / PENDING_VERIFICATION ──► ACTIVE ──► DISABLED

Transacción: sin marca ──► CONFIRMED_FRAUD (por contracargo)
```

---

## 11. Contrato de la API

### Endpoints principales
| Método | Ruta | Rol | Descripción |
|---|---|---|---|
| POST | `/v1/auth/register` | Público | Registro (RF-01) |
| POST | `/v1/auth/verify-email` | Público | Verificar código (RF-02) |
| POST | `/v1/auth/login` · `/refresh` · `/logout` | Público/Auth | Sesión (RF-03) |
| POST | `/v1/auth/forgot-password` · `/reset-password` | Público | Recuperación (RF-04) |
| POST/GET/PATCH | `/v1/users` | ADMIN | Usuarios y roles (RF-05) |
| POST/GET/DELETE | `/v1/api-keys` | ADMIN | Claves (RF-06) |
| **POST** | **`/v1/transactions/evaluate`** | **API key** | **Evaluación (RF-07)** |
| GET | `/v1/transactions/{id}` | Humanos | Detalle y explicación (RF-15) |
| POST/GET/PUT | `/v1/rules` | ANALYST | Reglas (RF-10) |
| POST | `/v1/rule-sets/publish` · `/rollback` | ADMIN/ANALYST | Versionado (RF-11) |
| PUT | `/v1/settings/thresholds` | ADMIN/ANALYST | Umbrales y política (RF-12) |
| POST/GET/DELETE | `/v1/lists` | ANALYST | Listas (RF-09) |
| GET | `/v1/cases` · POST `/v1/cases/{id}/claim` · `/resolve` | ANALYST | Casos (RF-14) |
| POST | `/v1/chargebacks` | ANALYST | Contracargos (RF-16) |
| GET | `/v1/metrics/summary` | Humanos | Dashboard (RF-17) |
| GET | `/v1/audit` | ADMIN/AUDITOR | Auditoría (RF-20) |
| POST | `/v1/auth/mfa/enroll` · `/mfa/verify` | Humanos | Autenticación multifactor (RF-22) |
| POST | `/v1/transactions/{id}/reveal` | ANALYST/AUDITOR | Revelar un dato sensible con justificación (RF-23) |

### Ejemplo: evaluar una transacción
**Petición**
```json
POST /v1/transactions/evaluate
X-API-Key: pp_live_xxxxxxxx
Idempotency-Key: 5f0c-...

{
  "transactionId": "ORD-88231",
  "amount": 3200.00,
  "currency": "PEN",
  "customerId": "cus_991",
  "emailHash": "9b74c9...",
  "deviceId": "dev_a1f3",
  "ipAddress": "190.x.x.x",
  "cardFingerprint": "fp_77ac",
  "cardCountry": "US",
  "shippingCountry": "PE",
  "occurredAt": "2026-10-07T22:30:00-05:00"
}
```

> Los identificadores personales (`deviceId`, `customerId`, `ipAddress`, `cardFingerprint`) viajan solo por TLS y se cifran antes de persistirse (sección 13).

**Respuesta**
```json
{
  "transactionId": "ORD-88231",
  "decision": "REJECT",
  "riskScore": 85,
  "reasons": [
    {"rule": "Dispositivo en múltiples cuentas", "points": 40},
    {"rule": "Cuenta nueva", "points": 20},
    {"rule": "Tarjeta de otro país", "points": 15},
    {"rule": "Monto alto", "points": 10}
  ],
  "ruleSetVersion": 12,
  "degraded": false,
  "latencyMs": 34
}
```

**Error (RFC 7807)**
```json
{
  "type": "https://pillapago.dev/errors/validation",
  "title": "Datos inválidos",
  "status": 400,
  "detail": "El campo 'amount' debe ser mayor que 0",
  "instance": "/v1/transactions/evaluate"
}
```

---

## 12. Arquitectura

### 12.1 Decisiones principales
| Decisión | Elegido | Justificación |
|---|---|---|
| Estilo | **Monolito modular** | Un equipo, un despliegue, recursos limitados. Los módulos están separados por funcionalidad y podrían extraerse después. |
| Microservicios | **No** | Añaden latencia de red, despliegues y trazabilidad distribuida que este alcance no justifica. Se reconsideran si el módulo de evaluación necesita escalar de forma independiente. |
| API Gateway | **No** | Con un solo servicio no hay qué enrutar. La autenticación, el límite de peticiones y el CORS se resuelven dentro de Spring. Se añadiría con varios servicios. |
| Capas | **Controller → Service → Repository** dentro de cada módulo | Estándar de la industria Java. |
| Núcleo del motor | **Java puro, sin Spring** | El evaluador de reglas es lógica de negocio crítica: se prueba de forma aislada y rápida. |
| Concurrencia | **Hilos virtuales (Java 21)** + pool de conexiones acotado | Mucha E/S (Redis, BD) sin bloquear hilos de plataforma. |
| Estado | **API stateless** | Permite escalar horizontalmente. |
| Terminación TLS | **Render (gestionado)** | Render termina TLS con certificado automático; en local no hay TLS. No es un API Gateway: no enruta ni agrega lógica. |

### 12.2 Diagrama de contexto
```
 ┌──────────────┐  API key / HTTPS   ┌────────────────────┐
 │ Tienda online│───────────────────►│                    │
 │ (comercio)   │◄───────────────────│     PILLAPAGO      │
 └──────────────┘  decisión (ms)     │   (API + motor)    │
                                     │                    │
 ┌──────────────┐  JWT / HTTPS       │                    │
 │  Analista /  │───────────────────►│                    │
 │  Admin /     │◄───────────────────│                    │
 │  Auditor     │   dashboard        └────────────────────┘
 └──────────────┘
```

### 12.3 Diagrama de contenedores
```
   Navegador (React + TS, Vercel) ── HTTPS ──┐
   Sistema del comercio ──────────── HTTPS ──┤
                                             ▼
                              ┌───────────────────────────┐
                              │  API Spring Boot 3        │
                              │  (Java 21, Docker, Render)│
                              │  módulos: auth, rules,    │
                              │  evaluation, cases...     │
                              │  puertos: Cola, Claves,   │
                              │  Archivos, Correo         │
                              └───┬──────────┬────────┬───┘
                                  │          │        │
              contadores y        ▼          │        ▼  adaptadores (por perfil)
              caché (rápido) ┌──────────┐    │   local: Floci (SQS, KMS, S3, SES)
                             │  Redis   │    │   nube:  outbox en Postgres, clave
                             └──────────┘    │          maestra secreta, R2, Resend/Brevo
                                             ▼
                              ┌───────────────────────┐
                              │ PostgreSQL (Neon)     │
                              └───────────────────────┘
```

### 12.4 Ruta crítica de evaluación (*hot path*)
La decisión **no toca PostgreSQL**. Esto la protege de la latencia y de los arranques en frío de Neon.

```
Petición ─► 1. Autenticar API key (caché en memoria)
         ─► 2. Idempotencia (Redis: ¿ya evaluada?)
         ─► 3. Listas bloqueo/confianza (caché en memoria)
         ─► 4. Métricas de velocity (Redis, claves seudonimizadas con HMAC)
         ─► 5. Evaluar reglas (versión activa en memoria)
         ─► 6. Decisión + guardar respuesta en Redis (idempotencia)
         ─► 7. RESPONDER
                │
                └─► (en segundo plano) cifrar campos personales y publicar evento ─► cola (local: SQS en Floci · nube: outbox en Postgres) ─► consumidor
                                                          ├─ guarda decisión y motivos en PostgreSQL
                                                          └─ si REVIEW: crea el caso
```

**Presupuesto de latencia (objetivo):** autenticación ≈ 1 ms · Redis ≈ 3–5 ms · reglas ≈ 1–2 ms · serialización y red ≈ 5–10 ms → **decenas de ms**.

### 12.5 Cómo se resuelven los retos técnicos
| Reto | Solución |
|---|---|
| **Velocity (conteos por ventana de tiempo)** | Redis con conjuntos ordenados por tiempo (ventana deslizante) y conjuntos con TTL para conteos distintos (ej. cuentas por dispositivo). Las claves usan HMAC por comercio: Redis nunca guarda identificadores en claro. |
| **Reglas sin redesplegar** | Se guardan como datos (JSON) y se versionan. Al publicar, cada instancia recarga su caché por evento. |
| **Evaluación segura de condiciones** | Lenguaje propio y acotado (campo, operador, valor). No se usa SpEL ni scripts, para evitar inyección de código. |
| **Idempotencia** | Redis (clave con TTL 24 h) + restricción única en BD `(merchant_id, transaction_id)`. |
| **Procesos pesados** | Cola detrás del puerto `ColaPort` (SQS en Floci en local, outbox en Postgres en la nube) con reintentos y DLQ para persistencia de decisiones, reportes y webhooks. |
| **Multi-tenant** | Todo filtrado por `merchant_id`; claves de Redis con prefijo de tenant; pruebas que verifican el aislamiento. |
| **Falla del motor** | Tiempo máximo de 300 ms y política fail-open / fail-closed configurable. |
| **Dos analistas, un caso** | Bloqueo optimista (`@Version`) en `review_cases`. |
| **Protección de datos personales** | Cifrado a nivel de campo, índices ciegos, enmascarado y auditoría encadenada (sección 13). |

### 12.6 Decisiones con compromiso aceptado (ADR resumidos)
- **ADR-01 · Persistencia asíncrona de decisiones.** Se responde antes de guardar en BD. *Riesgo:* si el proceso cae justo entre responder y publicar, se pierde el registro de auditoría. *Mitigación MVP:* la respuesta queda en Redis y un proceso de conciliación detecta huecos. *Fase 2:* patrón *outbox*.
- **ADR-02 · Redis como dependencia del hot path.** Si cae, se aplica la política de falla. Es un compromiso por latencia.
- **ADR-03 · Frontend React + TypeScript.** Es el que piden las ofertas de empleo Java/.NET. Rust en frontend queda descartado por ahora (poca demanda y más tiempo de aprendizaje).
- **ADR-04 · Sin PAN.** El comercio envía una huella (*fingerprint*) de la tarjeta; Pillapago nunca ve el número.
- **ADR-05 · Cifrado a nivel de campo con cifrado de sobre (Tink + KMS).** Protege los datos personales aunque se filtre la base de datos, un backup o un mensaje de cola. *Compromiso:* el servidor necesita el dato en claro en memoria para evaluar reglas, por lo que no existe cifrado de extremo a extremo entre el comercio y el analista (sección 13.1). *Costo:* gestión de claves y búsquedas limitadas a igualdad exacta (índices ciegos).
- **ADR-06 · Sin servicios AWS de pago en la nube.** El despliegue gratuito no usa AWS real. El código accede a cola, claves, archivos y correo mediante puertos; en local los adaptadores usan el SDK de AWS contra Floci (demuestra conocimiento de S3/SQS/KMS) y en la nube usan alternativas gratuitas. *Compromiso:* la clave maestra pasa de KMS a un secreto de la plataforma, que es más débil (13.1); *Mitigación:* cifrado de campo con Tink, clave solo en variable secreta, y perfil `aws` documentado para producción real.
- **ADR-07 · Podman rootless en lugar de Docker Desktop.** Menor superficie de ataque (sin demonio con privilegios) y sin licencia de escritorio. *Compromiso:* Testcontainers y los comandos de compose requieren configuración extra; se valida en la fase 0.
- **ADR-08 · Cola en Postgres (outbox) en la nube.** Evita un servicio de mensajería de pago y resuelve a la vez la pérdida de registros del ADR-01. *Compromiso:* menor throughput que SQS; suficiente para la demo.

### 12.7 Seguridad (resumen)
- Autenticación humana: JWT (15 min) + refresh rotativo. Autenticación máquina: API key hasheada.
- Autorización con `@PreAuthorize` por rol en servicios y pruebas de acceso por endpoint.
- Rate limiting con Bucket4j; CORS restringido; cabeceras de seguridad.
- Entradas validadas con Bean Validation; consultas parametrizadas (JPA).
- Secretos fuera del repositorio; `.env.example` sin valores reales.
- Dependencias analizadas en CI (Dependabot / OWASP Dependency-Check).
- El modelo completo de protección de datos (cifrado, enmascarado, auditoría y amenazas) está en la sección 13.

### 12.8 Portabilidad local y nube (puertos y adaptadores)

El negocio nunca llama al SDK de AWS ni a un proveedor concreto: depende de interfaces, y el perfil de Spring (`local` o `cloud`) elige el adaptador.

| Puerto | Perfil `local` | Perfil `cloud` (gratuito) |
|---|---|---|
| `ColaPort` | SQS en Floci | Outbox en Postgres (`FOR UPDATE SKIP LOCKED`) |
| `ClavesPort` | KMS en Floci | Clave maestra como secreto de Render + Tink |
| `ArchivosPort` (fase 2) | S3 en Floci | Cloudflare R2 (compatible con S3) |
| `CorreoPort` | SES en Floci o Mailpit | Resend o Brevo (plan gratuito) |
| Secretos | `.env` o SSM en Floci | Variables de entorno de Render |

Los límites de los planes gratuitos cambian; se verifican en la fase 0 y de nuevo antes del despliegue (R13). Un perfil `aws` con servicios reales queda documentado, sin desplegarse.

---

## 13. Seguridad y protección de datos

Este apartado define cómo se protege la información de cada transacción en todo su recorrido: comercio, Pillapago, analista y pantalla. Aplica los requisitos RF-22 a RF-25, RNF-29 a RNF-39 y las historias HU-16 y HU-17.

### 13.1 Qué se garantiza y qué no

| Se garantiza | Cómo |
|---|---|
| Un volcado de la base de datos, un backup, un mensaje de la cola o un log no revela datos personales. | Cifrado a nivel de campo, claves en KMS, logs sin datos personales. |
| Quien tiene acceso a la infraestructura (administrador de base de datos, desarrollador) no puede leer los datos en claro. | Las claves de datos solo se descifran con permisos de KMS que tiene la aplicación y no las personas. |
| Todo dato personal mostrado completo queda registrado: quién, cuándo, qué campo y por qué. | Enmascarado por defecto, revelado con justificación y auditoría encadenada. |
| Los datos viajan cifrados entre todos los componentes. | TLS en cada enlace (13.3). |
| Un comercio nunca ve datos de otro. | `merchant_id` tomado del token, identificadores opacos y cifrado con datos asociados al comercio. |

**Límites que deben quedar claros**
- Para evaluar reglas, el servidor necesita los datos **en claro en memoria**. Por eso no existe cifrado de extremo a extremo entre el comercio y el analista: el cifrado protege los datos en tránsito y en reposo, no durante el procesamiento.
- Si el servidor de aplicación se compromete, un atacante podría leer lo que esté en memoria en ese momento y usar las claves en uso. Se reduce con mínimo privilegio, claves con vida corta en memoria, monitoreo y un plan de respuesta a incidentes (R9).
- Un administrador con acceso total al servidor y a KMS es un riesgo interno; se mitiga con separación de funciones y auditoría, no se elimina.
- En la nube gratuita la clave maestra vive como secreto de la plataforma (ADR-06): quien administre Render puede acceder a ella. Es un compromiso aceptado por costo cero; con KMS real se restablece la separación de funciones.
- Este documento no es asesoría legal. En este proyecto los datos serán sintéticos; antes de tratar datos reales de personas hay que validar el cumplimiento con un abogado especializado (R10).

### 13.2 Clasificación de datos

| Clase | Ejemplos | Protección |
|---|---|---|
| **Secretos** | Contraseñas, API keys, refresh tokens, códigos de verificación, secreto TOTP | Contraseñas con BCrypt; API keys, refresh tokens y códigos solo como hash; secreto TOTP cifrado a nivel de campo. Nunca se muestran ni se registran. |
| **Datos personales** | IP, `deviceId`, `customerId`, `cardFingerprint`, valores de listas de bloqueo, correo (ya hasheado por el comercio) | Cifrado a nivel de campo (AES-256-GCM), índice ciego para búsquedas, enmascarado en la interfaz, revelado auditado. |
| **Datos de decisión** | Decisión, score, motivos, versión de reglas | Cifrado en reposo, acceso por comercio y rol, registros inmutables. |
| **Configuración del comercio** | Reglas, umbrales, política de falla | Confidencial por comercio; auditoría de cambios. |
| **Datos operativos** | Métricas agregadas, logs | Sin datos personales por diseño (RNF-34). |

### 13.3 Cifrado en tránsito

| Enlace | Protección |
|---|---|
| Navegador ↔ API | TLS 1.2 como mínimo (1.3 preferido) y HSTS. La terminación TLS la hace Render con certificado automático. |
| Sistema del comercio ↔ API | TLS; la API rechaza HTTP plano. |
| Frontend (Vercel) | HTTPS provisto por la plataforma. |
| API ↔ PostgreSQL (Neon) | TLS con verificación del certificado (`sslmode=verify-full`). |
| API ↔ Redis | Red interna de Render; en local, puerto publicado solo en 127.0.0.1, con contraseña. |
| API ↔ colas, claves, archivos y correo | HTTPS en la nube; en local, Floci en `http://localhost:4566` (solo desarrollo, datos sintéticos). |
| CI/CD ↔ Render | Despliegue por *deploy hook* de Render o integración con GitHub; sin credenciales en el repositorio. |

### 13.4 Cifrado en reposo

- **Base de datos:** Neon cifra el almacenamiento según su documentación (a verificar). No se depende solo de eso: el cifrado de campo protege los datos incluso ante acceso directo a la base.
- **Servicio de la API (Render):** sin datos persistentes en disco; todo el estado vive en Neon.
- **Objetos (fase 2):** cifrado del lado del servidor del proveedor (R2) y URL prefirmada de 15 minutos.
- **Redis:** sin persistencia (RDB y AOF desactivados), porque solo contiene contadores efímeros seudonimizados. La respuesta guardada para idempotencia no incluye datos personales.
- **Cola (outbox):** los campos personales del evento ya van cifrados antes de escribirse en la tabla.
- **Backups:** cifrados, con prueba periódica de restauración.

### 13.5 Cifrado a nivel de campo (cifrado de sobre)

```
KMS (clave maestra)
   │  GenerateDataKey / Decrypt
   ▼
Clave de datos por comercio (versionada)
   │  guardada cifrada en la tabla tenant_keys
   │  en memoria solo mientras se usa (caché con TTL de 15 min)
   ▼
AES-256-GCM(campo)  con datos asociados = merchant_id | tabla | columna | id de fila
   → se guarda: texto cifrado + nonce + versión de clave
```

- **No se implementa criptografía propia:** se usa Google Tink (AEAD) con la clave maestra detrás del puerto `ClavesPort`: AWS KMS (emulado por Floci) en local, y en la nube gratuita una clave maestra guardada como secreto de Render (ADR-06).
- Los **datos asociados** impiden copiar un valor cifrado a otra fila u otro comercio: el descifrado fallaría.
- **Rotación:** la clave maestra rota cada año; cada registro guarda la versión de su clave de datos y se re-cifra de forma perezosa.
- **Cripto-eliminación:** borrar la clave de datos de un comercio deja ilegibles todos sus datos, incluidos los de los backups.
- El cifrado ocurre fuera del camino de respuesta de la evaluación: antes de publicar el evento en SQS y al persistir.

### 13.6 Índices ciegos y seudonimización

- `device_hash = HMAC-SHA256(clave_de_índice_del_comercio, deviceId)`. La clave de índice es distinta de la clave de datos.
- Se usa en las columnas de búsqueda y en las claves de Redis (ej. `t:{merchantId}:dev:{hmac}:tx`), de modo que Redis nunca guarda el identificador real.
- **Límite:** solo permite igualdad exacta; no hay búsqueda parcial ni por rango sobre datos personales.

### 13.7 Enmascarado y revelado

| Campo | Se muestra como |
|---|---|
| IP | `190.xxx.xxx.12` |
| `deviceId` | `dev_****f3` |
| `customerId` | `cus_***91` |
| `cardFingerprint` | `fp_***ac` |
| Correo | No se muestra; solo un hash abreviado, porque el comercio ya lo envía hasheado |
| País, monto, decisión, score, motivos | Visibles |

Para revelar un campo se exige: rol autorizado (RISK_ANALYST o AUDITOR), MFA verificada en los últimos 15 minutos, justificación escrita y registro en auditoría. El valor se muestra 60 segundos. Superar 20 revelaciones por hora genera bloqueo y alerta (HU-16).

### 13.8 Protección contra exposición accidental

- Logs, trazas, métricas y mensajes de error sin datos personales (filtro de redacción más pruebas automáticas).
- Mensajes de error genéricos que no revelan si un correo o recurso existe.
- Cabeceras: `Cache-Control: no-store` en respuestas sensibles, CSP estricta, `X-Content-Type-Options: nosniff` y `Referrer-Policy: no-referrer`.
- Identificadores opacos (UUID) en todas las rutas.
- Exportaciones (fase 2): enmascaradas por defecto, cifradas en S3 y con URL prefirmada de 15 minutos.
- Entornos de desarrollo y pruebas: solo datos sintéticos; nunca se copia producción a local.
- Capturas de pantalla o impresión por parte de usuarios autorizados quedan fuera del control técnico y se tratan como riesgo residual mediante política interna.

### 13.9 Gestión de accesos y claves

- **MFA obligatoria** para los roles con acceso a datos sensibles (RF-22).
- **Mínimo privilegio:** la API solo recibe los secretos que necesita; en el perfil `aws`, solo `kms:Decrypt`, `kms:GenerateDataKey`, su cola y su bucket.
- **Separación de funciones:** quien despliega no administra las claves; en un proyecto individual se documenta como limitación (ADR-06).
- **Base de datos:** usuario de la aplicación sin permisos de DDL; usuario de migraciones aparte; `audit_log` con permiso de solo inserción.
- **Secretos:** variables de entorno de Render en la nube y `.env` local (SSM de Floci opcional); nunca en el repositorio; `.env.example` sin valores reales; escaneo de secretos con gitleaks en CI.
- **Rotación:** clave maestra anual, API keys rotables y clave de firma de JWT con identificador de versión (`kid`).

### 13.10 Auditoría a prueba de manipulación

- **Eventos registrados:** inicios de sesión, cambios de reglas, roles y claves, resoluciones de casos, revelación de datos, exportaciones y accesos denegados.
- **Solo inserción:** el rol de base de datos de la aplicación no puede modificar ni borrar filas de `audit_log`.
- **Encadenado de hashes:** cada fila guarda `hash = SHA-256(hash_anterior || contenido)`. Una verificación diaria recorre la cadena y alerta si se rompe.
- **Sin el dato revelado:** se registra qué campo, de qué transacción, quién, cuándo y con qué motivo; nunca el valor.
- **Retención:** política propia definida con asesoría legal; por defecto igual a la de los datos.
- **Fase 2:** anclar el hash diario en S3 con Object Lock (WORM).

### 13.11 Modelo de amenazas (resumen)

| Amenaza | Control |
|---|---|
| Copia de la base de datos o de un backup | Cifrado de campo, claves en KMS, backups cifrados |
| Personal con acceso a la infraestructura lee datos | Datos cifrados, sin permiso de descifrado para personas, auditoría |
| Analista que consulta datos sin necesidad | Enmascarado, revelado con justificación y MFA, límite y alertas |
| Un comercio intenta ver datos de otro (IDOR) | `merchant_id` del token, UUID opacos, datos asociados en el cifrado, pruebas de aislamiento |
| Robo de sesión (XSS) | Token en memoria, refresh en cookie HttpOnly, CSP, MFA |
| Credenciales filtradas o fuerza bruta | BCrypt, límite de intentos, bloqueo, MFA |
| Datos personales en logs | Redacción y pruebas automáticas |
| Alteración del historial de auditoría | Solo inserción y encadenado de hashes |
| API key robada | Solo el hash en la base de datos, rotación y revocación, límite de peticiones |
| Interceptación en la red | TLS en todos los canales |
| Servidor de aplicación comprometido | Riesgo residual: mínimo privilegio, claves con TTL corto, monitoreo (13.1) |

### 13.12 Cumplimiento (referencia, no asesoría legal)

- **Finalidad y proporcionalidad:** solo se reciben los campos necesarios para evaluar riesgo; no se recibe PAN, nombre ni dirección.
- **Seguridad:** las medidas técnicas de esta sección.
- **Roles:** el comercio decide para qué se usan los datos y Pillapago los trata por encargo suyo, lo que normalmente exige un contrato o cláusula de encargo de tratamiento. Los términos exactos deben validarse con la norma vigente.
- **Derechos ARCO** (acceso, rectificación, cancelación y oposición): RF-24.
- **Retención y eliminación:** RF-25, RNF-16 y cripto-eliminación.
- **Incidentes:** procedimiento documentado de detección, contención, evaluación, aviso al comercio sin demora y registro.
- **PCI-DSS:** se minimiza el alcance al no recibir PAN; no hay certificación.

---

## 14. Estructura de carpetas

Se organiza **por funcionalidad** (*package by feature*): todo lo de una funcionalidad vive junto y las capas Controller / Service / Repository están dentro de cada una.

```
pillapago/
├── backend/
│   ├── src/main/java/dev/pillapago/
│   │   ├── auth/                 # registro, login, verificación, recuperación
│   │   │   ├── controller/
│   │   │   ├── service/
│   │   │   ├── repository/
│   │   │   ├── domain/           # entidades JPA
│   │   │   └── dto/              # Java Records
│   │   ├── tenant/               # comercios, usuarios, roles, API keys
│   │   ├── evaluation/           # endpoint de evaluación (hot path)
│   │   │   ├── controller/
│   │   │   ├── service/
│   │   │   └── engine/           # NÚCLEO: Java puro, sin Spring
│   │   │       ├── Rule.java
│   │   │       ├── Condition.java
│   │   │       ├── RuleEvaluator.java
│   │   │       └── ScoreCalculator.java
│   │   ├── rules/                # CRUD, versionado y publicación
│   │   ├── lists/                # listas de bloqueo / confianza
│   │   ├── cases/                # cola de revisión
│   │   ├── chargeback/           # contracargos y efectividad
│   │   ├── metrics/              # dashboard
│   │   ├── audit/                # auditoría con cadena de hashes
│   │   ├── messaging/            # productores/consumidores sobre ColaPort
│   │   ├── integration/          # puertos y adaptadores: claves, archivos, correo (local | cloud)
│   │   └── shared/               # reutilizable (equivale a core/util)
│   │       ├── config/           # seguridad, Redis, SQS, CORS
│   │       ├── exception/        # @RestControllerAdvice + ProblemDetail
│   │       ├── security/         # filtros JWT y API key
│   │       ├── crypto/           # cifrado de campo (Tink + KMS), HMAC, enmascarado
│   │       └── util/             # hashing, fechas, utilidades puras
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   ├── application-local.yml
│   │   └── db/migration/         # Flyway: V1__init.sql ...
│   ├── src/test/java/            # unitarias, integración, ArchUnit
│   ├── Dockerfile
│   └── pom.xml
├── frontend/                     # React + TypeScript (Vite)
│   ├── src/ (pages, components, api, hooks)
│   └── package.json
├── simulator/                    # generador de tráfico (HU-14)
├── load-tests/                   # scripts k6 / Gatling
├── docs/                         # esta especificación, diagramas, ADRs
├── .github/workflows/ci-cd.yml
├── compose.yaml                  # PostgreSQL + Redis + Floci (podman compose)
├── .env.example
├── .gitignore
└── README.md
```

**Reglas de dependencia** (verificadas con ArchUnit):
- `controller` → `service` → `repository`; nunca al revés ni saltando capas.
- `engine/` no importa nada de Spring ni de JPA.
- Un módulo no accede al repositorio de otro; se comunica por su `service`.
- `shared/` no depende de ningún módulo de negocio.
- Ningún módulo cifra, descifra ni calcula HMAC por su cuenta: todo pasa por `shared/crypto`.
- El código de negocio depende de los puertos de `integration/` y `messaging/`, nunca del SDK de AWS ni de un proveedor concreto.

**Dónde va cada cosa:** las reglas de negocio centrales viven en `evaluation/engine` y en los `service` de cada módulo; lo reutilizable y transversal, en `shared/`. No se crea una carpeta `core` global que se vuelva un cajón de todo.

---

## 15. Stack tecnológico

| Área | Tecnología | Motivo |
|---|---|---|
| Lenguaje / runtime | Java 21 (hilos virtuales) | Alta demanda; buen manejo de concurrencia de E/S. |
| Framework | Spring Boot 3.x (Web, Security, Data JPA, Validation, Actuator) | Estándar empresarial. |
| Base de datos | PostgreSQL (Neon en nube, contenedor en local) + Flyway | Relacional, migraciones versionadas. |
| Caché y contadores | Redis (contenedor local; Render Key Value o Upstash en nube) | Ventanas de tiempo e idempotencia con baja latencia. |
| Mensajería | `ColaPort`: SQS en Floci (local) / outbox en Postgres (nube) | Procesos asíncronos con reintentos y DLQ, sin costo. |
| Archivos (fase 2) | `ArchivosPort`: S3 en Floci / Cloudflare R2 | Reportes con URL prefirmada. |
| Mapeo / reducción de código | MapStruct, Lombok, Java Records | DRY en DTOs y entidades. |
| Seguridad | Spring Security, JJWT, BCrypt, Bucket4j | JWT, hashing, límite de peticiones. |
| Criptografía | Google Tink (AEAD AES-256-GCM), `ClavesPort` (KMS en Floci / clave secreta), HMAC-SHA256 | Cifrado de campo e índices ciegos sin criptografía propia. |
| Secretos | Variables de entorno (Render) / `.env` local | Credenciales fuera del código. |
| Correo | `CorreoPort`: SES en Floci o Mailpit / Resend o Brevo | Códigos de verificación y recuperación. |
| MFA | TOTP (RFC 6238) | Segundo factor con app autenticadora. |
| TLS | Gestionado por Render y Vercel | Sin servidor propio que mantener. |
| Escaneo de secretos | gitleaks (CI) | Evita subir credenciales al repositorio. |
| Frontend | React + TypeScript + Vite en Vercel | Demanda laboral y rapidez de desarrollo. |
| Pruebas | JUnit 5, Mockito, Testcontainers, ArchUnit, k6/Gatling | Unitarias, integración, arquitectura y carga. |
| Observabilidad | Micrometer + Actuator (Grafana opcional) | Métricas y *health checks*. |
| Contenedores | Podman (rootless, WSL2) + `podman compose` | Sin demonio con privilegios; sin Docker Desktop. |
| CI/CD | GitHub Actions + deploy hook de Render | Reproducibilidad y despliegue automático. |
| Documentación API | springdoc-openapi (Swagger UI) | Contrato siempre al día. |

---

## 16. Entornos, despliegue y CI/CD

### Local (Windows + Podman rootless sobre WSL2)
- **Motor:** Podman con su máquina sobre WSL2; Podman Desktop como interfaz gráfica opcional. No se instala Docker Desktop ni Portainer, y no se monta ningún socket del motor dentro de contenedores (RNF-40).
- **Memoria:** con WSL2 los límites se fijan en `%UserProfile%\.wslconfig` (`memory=3GB`); las opciones `--memory` y `--disk-size` de `podman machine init` no se aplican con el proveedor WSL (a verificar en la versión instalada).
- **Puertos:** se conservan los estándar de cada herramienta y se publican **solo en 127.0.0.1**. Solo se remapea si hay un conflicto real. Si un puerto falla sin motivo aparente, revisar los rangos reservados por Windows con `netsh interface ipv4 show excludedportrange protocol=tcp`.

| Servicio | Puerto | Dónde corre |
|---|---|---|
| API Spring Boot | 8080 | Host (Maven) |
| Frontend Vite | 5173 | Host (npm) |
| PostgreSQL | 5432 | Contenedor |
| Redis | 6379 | Contenedor |
| Floci (AWS emulado) | 4566 | Contenedor |

```yaml
# compose.yaml (resumen)
services:
  postgres:
    image: postgres:16-alpine
    ports: ["127.0.0.1:5432:5432"]
    environment: { POSTGRES_PASSWORD: "${PG_PASSWORD}" }
  redis:
    image: redis:7-alpine
    command: ["redis-server", "--requirepass", "${REDIS_PASSWORD}", "--save", "", "--appendonly", "no"]
    ports: ["127.0.0.1:6379:6379"]
  floci:
    image: floci/floci:latest        # fijar versión tras la prueba de humo
    ports: ["127.0.0.1:4566:4566"]
    environment: { FLOCI_STORAGE_MODE: persistent }
    volumes: ["floci-data:/app/data"]  # sin esto, las claves KMS se pierden al reiniciar
volumes: { floci-data: {} }
```
Ejecución: `podman compose up -d`, luego `mvn spring-boot:run -Dspring-boot.run.profiles=local` y `npm run dev` en `frontend/`.

### Producción (demo gratuita, sin AWS)
```
Usuario ─► Vercel (frontend React)
              │ HTTPS
              ▼
        Render (API Java en Docker, plan gratuito)
              ├──► Neon (PostgreSQL + outbox)
              ├──► Redis (Render Key Value gratuito o Upstash)
              ├──► Resend / Brevo (correo)
              └──► Cloudflare R2 (reportes, fase 2)
```
- La instancia gratuita de Render tiene 512 MB de RAM y 0.1 CPU, y se apaga tras 15 min sin tráfico (despertar ≈ 1 min). Neon también se suspende por inactividad: el primer acceso tras un periodo ocioso paga **ambos** arranques.
- El Postgres gratuito de Render caduca, por eso la base de datos va en Neon.
- JVM sugerida: `-Xmx300m -XX:+UseSerialGC`. Un ping periódico externo puede evitar el apagado, respetando las horas gratuitas del plan.
- Vercel (plan Hobby) es solo para uso no comercial: válido para el portafolio.
- Los límites y condiciones de los planes gratuitos se verifican antes de desplegar (R13).

### CI/CD (GitHub Actions)
1. Compilar y ejecutar pruebas (`mvn verify`) con Testcontainers.
2. Análisis de cobertura (JaCoCo), de dependencias y de secretos expuestos (gitleaks).
3. Construir imagen multietapa (Render también puede construirla desde el Dockerfile).
4. Disparar el *deploy hook* de Render en la rama `main`; Vercel despliega el frontend desde GitHub.
5. Ejecutar prueba de humo (*smoke test*) contra el despliegue, tolerando el arranque en frío.

---

## 17. Estrategia de pruebas

| Nivel | Qué prueba | Herramienta |
|---|---|---|
| Unitarias | Motor de reglas, cálculo de score, servicios | JUnit 5, Mockito |
| Integración | Repositorios, Redis, SQS, seguridad | Testcontainers |
| Contrato / API | Esquemas, códigos HTTP, errores RFC 7807 | Colección Postman / REST-assured |
| Seguridad | Acceso por rol; aislamiento entre tenants | Pruebas automatizadas |
| Cifrado y privacidad | Una consulta directa a la BD solo devuelve texto cifrado; los datos asociados impiden copiar valores entre filas; no hay datos personales en logs | Testcontainers + pruebas dedicadas |
| Auditoría | Detección de alteraciones en la cadena de hashes | Pruebas de integración |
| Vulnerabilidades | Análisis dinámico básico de la API | OWASP ZAP baseline (CI) |
| Arquitectura | Reglas de dependencia entre capas y módulos | ArchUnit |
| Rendimiento | RNF-01 y RNF-03 con tráfico simulado | k6 / Gatling |
| Accesibilidad | RNF-22 en el dashboard | axe-core / Lighthouse |

> Testcontainers sobre Podman requiere activar el socket compatible y definir `DOCKER_HOST`; se valida en la fase 0.

Cada historia de usuario se considera terminada solo si cumple sus criterios de aceptación con pruebas automatizadas.

---

## 18. Riesgos y supuestos

| # | Riesgo / supuesto | Impacto | Mitigación |
|---|---|---|---|
| R1 | **Floci:** su documentación lista S3, SQS, KMS, SES y SSM, pero la compatibilidad con nuestro uso (SSE-KMS, DLQ, Tink + KMS) no está probada. | Medio | Prueba de humo en la fase 0; alternativa: LocalStack. |
| R2 | La instancia gratuita de Render (512 MB, 0.1 CPU) es muy ajustada para la JVM. | Alto | Heap acotado y GC serial; Redis fuera del contenedor; medir arranque y memoria pronto. |
| R3 | Los objetivos de latencia no se cumplen en la demo gratuita. | Medio | Validar RNF-01 en local y documentar el hardware; presentar la nube como demostración. |
| R4 | Sin datos reales, la efectividad de las reglas no puede validarse. | Alto | Simulador con patrones de fraude conocidos; dejar claro que es una demostración. |
| R5 | Pérdida de auditoría ante caída en ADR-01. | Medio | Conciliación en MVP; outbox (ADR-08) elimina el hueco en la nube. |
| R6 | Alcance excesivo. | Alto | Respetar el corte mínimo (sección 5); todo lo "Fase 2" queda fuera hasta terminarlo. |
| R7 | Nombre comercial ya registrado. | Bajo | Verificación previa (sección 1). |
| R8 | Complejidad del cifrado de campo. | Medio | Usar Tink; caché de claves en memoria; cifrado fuera del camino de respuesta; medir con k6. |
| R9 | Un servidor comprometido ve datos en memoria (13.1). | Alto | Mínimo privilegio, claves con TTL corto, monitoreo y plan de incidentes. |
| R10 | Cumplimiento legal sin validación profesional. | Alto | Datos sintéticos; asesoría legal antes de usar datos reales. |
| R11 | Arranque en frío en cadena (Render ≈ 1 min + Neon). | Medio | Aceptarlo en la demo; ping de mantenimiento; indicar en el README. |
| R12 | Testcontainers y compose sobre Podman requieren configuración propia. | Medio | Validar en la fase 0; plan B: Docker Engine dentro de WSL2. |
| R13 | Los planes gratuitos (Render, Neon, Vercel, Upstash, R2, Resend/Brevo) pueden cambiar límites o condiciones. | Medio | Verificar antes de desplegar; mantener los adaptadores intercambiables. |
| R14 | Clave maestra como secreto de plataforma (ADR-06). | Medio | Documentarlo; solo datos sintéticos; perfil `aws` como camino a producción real. |
| S1 | Los comercios no envían datos de tarjeta, solo huellas. | — | Fuera de alcance PCI-DSS. |
| S2 | El proyecto es de portafolio; no hay clientes reales. | — | Se presenta como caso de uso realista. |
| S3 | Todos los datos del portafolio son sintéticos. | — | Con datos reales haría falta validación legal previa (13.12). |
| S4 | El proyecto no genera costos por uso en la nube. | — | Sin servicios AWS reales; revisar topes de cada plan gratuito. |

---

## 19. Hoja de ruta por fases

| Fase | Contenido | Resultado |
|---|---|---|
| **0 · Preparación** | WSL2 + Podman rootless; prueba de humo de Floci (SQS con DLQ, KMS, SES, SSM) y de Testcontainers sobre Podman; repo, `compose.yaml`, esqueleto Spring, CI básico | Entorno reproducible y supuestos validados |
| **1 · Núcleo** | Motor de reglas en Java puro + pruebas unitarias; RF-07, RF-08 | Decisiones correctas en memoria |
| **2 · Hot path** | Redis (velocity, idempotencia), caché de reglas, medición de latencia en local | RNF-01 validado |
| **3 · Identidad** | RF-01 a RF-06, RBAC, rate limiting (MFA puede diferirse) | Acceso seguro por rol |
| **3b · Protección de datos** | `shared/crypto` (Tink + `ClavesPort`), cifrado de campo, índices ciegos, enmascarado y revelado (RF-23) | Datos protegidos antes de persistirlos |
| **4 · Gestión** | Reglas (RF-10 a RF-12), listas, casos, contracargos, umbrales (HU-18), auditoría (HU-19) | Flujo del analista completo |
| **5 · Persistencia asíncrona** | `ColaPort` con adaptador SQS y adaptador outbox, consumidores, DLQ, conciliación | ADR-01 y ADR-08 implementados |
| **6 · Frontend y dashboard** | React, métricas, accesibilidad | Demo visible |
| **7 · Simulador y carga** | RF-21, pruebas de carga con k6 en local | Números para el README |
| **8 · Nube gratuita** | Render + Neon + Vercel, correo, CI/CD completo, perfil `cloud` | Demo pública sin costo |
| **9 · Fase 2** | MFA y auditoría encadenada (si se difirieron), simulación de reglas, webhooks, reportes, ML | Mejoras |

---

## 20. Historial de versiones

| Versión | Fecha | Cambios |
|---|---|---|
| v0.1 | 2026-10-07 | Primera versión: problema, alcance, requisitos, historias de usuario y arquitectura. |
| v0.2 | 2026-10-07 | Se eliminan los emojis; sección 13 de seguridad y protección de datos; RF-22 a RF-25, RNF-29 a RNF-39, HU-16, HU-17 y ADR-05; fase 3b. |
| v0.3 | 2026-10-08 | Nombre del producto cambiado de Centinela a Pillapago (paquetes, prefijo de API key y ejemplos). Despliegue gratuito sin AWS (Render + Vercel + Neon); puertos y adaptadores (12.8); ADR-06 a ADR-08; entorno local con Podman rootless y política de puertos; RNF-01, 04, 07, 09, 14, 28 a 30, 36 ajustados y RNF-40 nuevo; HU-18 y HU-19; corte mínimo del MVP; riesgos R2 y R11 a R14; hoja de ruta actualizada. |

---

## Licencia
MIT — ver archivo `LICENSE`.
