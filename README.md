# Pillapago — Motor de Decisión Antifraude en Tiempo Real

[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://adoptium.net/)
[![Spring Boot 3.2](https://img.shields.io/badge/Spring%20Boot-3.2.4-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![React 18](https://img.shields.io/badge/React-18.3-blue.svg)](https://react.dev/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.4-blue.svg)](https://www.typescriptlang.org/)
[![PostgreSQL 16](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org/)
[![Redis 7](https://img.shields.io/badge/Redis-7-red.svg)](https://redis.io/)

---

## 📋 Descripción General

**Pillapago** es una plataforma SaaS multi-tenant diseñada para la prevención y detección de fraude en pagos online en tiempo real. 

El sistema evalúa transacciones entrantes con una latencia inferior a **50 ms en el Hot Path**, aplicando capas sucesivas de validación:
1. **Listas de Bloqueo y Confianza en memoria RAM ($O(1)$)** con short-circuit instantáneo.
2. **Control de Idempotencia y Contadores de Velocidad** sobre Redis en ventanas deslizantes (10 min, 1 hora, 24 horas).
3. **Motor de Reglas Dinámico en Memoria** con versionado inmutable ($v1, v2, \dots$) y despliegue sin caída de servicio (*zero-downtime*).
4. **Cola de Revisión Manual** para transacciones sospechosas con control de concurrencia optimista (`@Version` JPA) y resolución en 3 clics.

---

## 🏛️ Arquitectura del Sistema

```
                                [ Cliente / Pasarela de Pago ]
                                               │
                                     (POST /v1/evaluations)
                                               │
                                               ▼
                              ┌─────────────────────────────────┐
                              │     API Gateway / Spring Boot   │
                              └────────────────┬────────────────┘
                                               │
               ┌───────────────────────────────┴───────────────────────────────┐
               │                                                               │
               ▼                                                               ▼
    [ 1. ListCacheService (RAM) ]                                   [ Frontend Dashboard ]
    - Blocklist / Allowlist O(1)                                    - React 18 + Vite + TS
    - Short-circuit instantaneo:                                    - Cola de Casos (HU-09)
      * BLOCK -> REJECT (Score 100)                                 - Gestión de Listas (HU-13)
      * ALLOW -> APPROVE (Score 0)                                  - Versionado Reglas (HU-08)
               │ (Sin match)
               ▼
    [ 2. Redis Cluster (RAM) ]
    - Idempotencia SHA-256 (24h)
    - Velocidad (10m, 1h, 24h)
               │
               ▼
    [ 3. Rule Engine Cache (RAM) ]
    - AtomicReference<RuleSetVersion>
    - Versionado inmutable vN
    - Evaluador de condiciones
               │
               ▼
    [ 4. Base de Datos / Async ]
    - Persistencia asíncrona (Virtual Threads)
    - Casos en revisión (PENDING_REVIEW)
    - Cifrado Tink AES-256-GCM + Blind Index
```

---

## 🚀 Capacidades y Módulos Implementados

### 1. Autenticación, Auditoría y Multi-tenancy (HU-01, HU-02)
- Registro seguro de comercios y verificación de correo en 2 pasos (código de 6 dígitos con expiración).
- Criptografía de contraseñas con **Argon2id** (OWASP compliant).
- Autenticación con JWT de corta duración y rotación de Refresh Tokens gestionados en Redis con revocación atómica.
- Aislamiento estricto multi-tenant por `merchant_id` en todos los repositorios y servicios.

### 2. Gestión de Credenciales y API Keys (HU-05)
- Emisión de llaves públicas (`pk_live_...`) y privadas (`sk_live_...`) para comercios.
- Almacenamiento con hashing SHA-256 (nunca se almacena la clave privada en texto plano).
- Autenticación transparente mediante header `X-API-KEY` en endpoints de ingesta.

### 3. Motor de Decisión Antifraude (HU-06, HU-06b, HU-06c)
- Ingesta de transacciones mediante `POST /v1/evaluations`.
- Prevención de duplicados mediante control de idempotencia respaldado en Redis.
- Contadores de velocidad en tiempo real para tarjeta, IP y dispositivo.
- Políticas de degradación elegante ante indisponibilidad de Redis (`FAIL_OPEN` o `FAIL_CLOSED`).
- Persistencia asíncrona mediante hilos virtuales de Java 21 para no bloquear el Hot Path.

### 4. Versionado Atómico de Conjuntos de Reglas (HU-08)
- Publicación inmutable de reglas de negocio en versiones secuenciales (`v1`, `v2`, etc.).
- Recarga en caliente con *zero-downtime* respaldada por `AtomicReference` en memoria.
- Soporte para **Rollback instantáneo** hacia cualquier versión previa.
- Detección de diferencias entre borrador y versión activa (*draft-diff*).

### 5. Cola de Revisión y Resolución de Casos (HU-09)
- Bandeja de entrada para transacciones marcadas como `PENDING_REVIEW`.
- Asignación de casos al analista (`ASSIGNED`) y resolución como `APPROVED` o `REJECTED`.
- **Control de concurrencia optimista** (`@Version` JPA): previene que dos analistas resuelvan el mismo caso simultáneamente, respondiendo `409 Conflict`.
- Ergonomía de diseño: resolución completa en **3 clics** desde la cola.
- Casos resueltos convertidos en inmutables.

### 6. Listas de Bloqueo y Confianza (HU-13)
- Gestión de entidades sospechosas o de confianza (`DEVICE`, `IP`, `EMAIL`, `CARD_FINGERPRINT`).
- Búsqueda en memoria RAM con complejidad **$O(1)$** libre de bloqueos (*lock-free*).
- **Short-circuit en Hot Path:** Coincidencia en `BLOCK` devuelve `REJECT` inmediato (Score 100); coincidencia en `ALLOW` devuelve `APPROVE` inmediato (Score 0).
- Expiración temporal automática de elementos.
- Control de acceso RBAC estricto: exclusivo para el rol `RISK_ANALYST` (403 Forbidden para otros roles).

### 7. Privacidad y Criptografía de Campo (RNF-15)
- Cifrado autenticado en reposo de datos sensibles de clientes con **Google Tink (AES-256-GCM)**.
- Índices ciegos deterministas con **HMAC-SHA256** para permitir búsquedas exactas sin descifrar.
- Enmascaramiento dinámico en frontend y logs (`203.0.***.***`, `u***r@correo.com`).

---

## 📊 Diagramas de Arquitectura y Ciclo de Vida E2E

Los siguientes diagramas detallan el diseño técnico y el flujo de ejecución de los componentes:

| Diagrama | Descripción |
|---|---|
| [Ciclo de Autenticación E2E](docs/diagrams/hu02_auth_lifecycle_e2e.svg) | Registro, verificación de correo, emisión de JWT y refresh tokens. |
| [Ciclo de Vida de API Keys](docs/diagrams/hu05_api_key_lifecycle_e2e.svg) | Creación, hash SHA-256, autenticación en Hot Path y revocación. |
| [Evaluación del Motor de Reglas](docs/diagrams/hu06_rule_engine_evaluation_e2e.svg) | Flujo del evaluador de transacciones y cálculo de score. |
| [Velocidad e Idempotencia](docs/diagrams/hu06b_velocity_idempotency_e2e.svg) | Ventanas deslizantes en Redis y deduplicación de peticiones. |
| [Publicación Atómica de Reglas](docs/diagrams/hu08_rule_set_versioning_lifecycle_e2e.svg) | Despliegue inmutable zero-downtime y rollback en RAM. |
| [Resolución Concurrente de Casos](docs/diagrams/hu09_case_resolution_lifecycle_e2e.svg) | Cola de analistas, asignación y bloqueo optimista JPA. |
| [Listas de Bloqueo y Confianza](docs/diagrams/hu13_list_management_lifecycle_e2e.svg) | Caché en memoria O(1), cifrado Tink y short-circuit en Hot Path. |
| [Cifrado de Nivel de Campo](docs/diagrams/enc_field_encryption_e2e.svg) | Blind Index HMAC-SHA256 y cifrado AES-256-GCM. |

---

## 📁 Estructura del Repositorio

```
pillapago/
├── compose.yaml                # Orquestación local: PostgreSQL 16 (puerto 5433) y Redis 7 (puerto 6379)
├── .env.example                # Variables de entorno base para desarrollo local
├── backend/                    # API Backend (Spring Boot 3.2.4 + Java 21)
│   ├── mvnw / mvnw.cmd         # Maven Wrapper portátil
│   ├── pom.xml                 # Dependencias (Flyway, Redis, Security, Tink, Testcontainers)
│   ├── src/main/java/com/pillapago/
│   │   ├── auth/               # Autenticación, JWT, Argon2id y usuarios
│   │   ├── evaluation/         # Motor de decisión, scoring, persistencia asíncrona y casos
│   │   ├── lists/              # Listas de bloqueo y confianza con caché en memoria O(1)
│   │   ├── merchant/           # Gestión multi-tenant de comercios y API keys
│   │   ├── rules/              # Versionado inmutable de reglas, draft-diff y rollback
│   │   └── shared/             # Criptografía Tink, blind indexes, manejo global de errores
│   └── src/main/resources/
│       ├── application.yml     # Configuración de perfiles (dev, test, prod)
│       └── db/migration/       # Migraciones Flyway acumuladas (V1 a V12)
├── frontend/                   # Dashboard Web (React 18 + TypeScript + Vite)
│   ├── src/
│   │   ├── api/                # Clientes Axios tipados con interceptor de JWT
│   │   ├── components/         # Componentes reutilizables y rutas protegidas (RBAC)
│   │   ├── context/            # AuthContext (estado en memoria y control de sesión)
│   │   ├── pages/              # Vistas: Dashboard, Casos, Reglas, Listas, Auditoría, Login
│   │   └── styles/             # Hojas de estilo modulares (sin CSS inline)
│   └── package.json
└── docs/                       # Especificaciones funcionales, resúmenes de módulos y diagramas UML
```

---

## 🛠️ Requisitos Previos

1. **Java 21 JDK** (Eclipse Temurin 21 o Amazon Corretto 21).
2. **Node.js 18+** y **npm** (para la interfaz web).
3. **Podman Desktop** o **Docker Desktop** (para PostgreSQL y Redis).
4. **Git**.

---

## 🚀 Guía de Despliegue Local (Paso a Paso)

### 1. Clonar el repositorio
```bash
git clone https://github.com/Winan03/pillapago.git
cd pillapago
```

### 2. Configurar variables de entorno
```bash
# En Windows (PowerShell):
Copy-Item .env.example .env

# En Linux / macOS:
cp .env.example .env
```

### 3. Levantar infraestructura en contenedores (PostgreSQL & Redis)
```bash
podman compose up -d
# O con Docker:
# docker compose up -d
```
Los servicios quedarán expuestos en:
- **PostgreSQL 16:** `127.0.0.1:5433` (BD: `pillapago`, usuario: `pillapago_user`, password: `pillapago_pass`)
- **Redis 7:** `127.0.0.1:6379` (password: `redis_pass`)

### 4. Iniciar el Backend (Spring Boot)
```bash
cd backend

# En Windows:
.\mvnw.cmd spring-boot:run

# En Linux / macOS:
./mvnw spring-boot:run
```
> *Flyway ejecutará automáticamente las migraciones desde `V1` hasta `V12`.*

Verificar estado en:
```bash
curl http://localhost:8080/actuator/health
```

### 5. Iniciar el Frontend (React + Vite)
En una nueva terminal:
```bash
cd frontend
npm install
npm run dev
```
La aplicación web estará disponible en `http://localhost:5173`.

---

## 🧪 Pruebas y Control de Calidad

### Backend
```bash
cd backend

# Ejecutar todas las pruebas unitarias y de integración:
.\mvnw.cmd test

# Ejecutar una suite específica (ej. Listas de Bloqueo E2E):
.\mvnw.cmd test -Dtest="ListManagementE2ETest"

# Verificación completa con reporte JaCoCo:
.\mvnw.cmd clean verify
```

### Frontend
```bash
cd frontend

# Chequeo de tipos TypeScript en modo estricto:
npm run type-check

# Análisis estático de código:
npm run lint

# Compilación de producción:
npm run build
```

---

## 🔒 Estándares de Seguridad y Buenas Prácticas

- **RNF-08 (Concurrencia Optimista):** Control de acceso concurrente mediante versiones JPA en transacciones de analistas para evitar condiciones de carrera.
- **RNF-15 (Privacidad y Criptografía):** Datos confidenciales protegidos con cifrado de sobre (Envelope Encryption) con Google Tink y Blind Indexes deterministas.
- **RNF-30 (Memoria y Desempeño en Redis):** Redis configurado como almacén volátil sin persistencia en disco (`--save ""` y `--appendonly no`) para garantizar latencias inferiores a 2 ms.
- **RNF-40 (Mínimo Privilegio):** Enlaces de red restringidos estrictamente a `127.0.0.1` en desarrollo local.
