# Pillapago - Motor de Decisión Antifraude

## 📋 Descripción general
Pillapago es un motor de decisión antifraude en tiempo real para comercios online, construido con **Spring Boot 3 + Java 21**, PostgreSQL y **Podman**.

---

## 🛠️ Requisitos previos
Para clonar y levantar el proyecto localmente necesitas:
1. **Git**
2. **Podman Desktop** (o Docker Desktop) en ejecución.
3. **Java 21 JDK** (Recomendado: Eclipse Temurin 21 o Amazon Corretto 21).
   > *Nota:* **No** necesitas tener Maven instalado en el sistema operativo, ya que el proyecto incluye el **Maven Wrapper (`mvnw`)**.

---

## 🚀 Levantamiento en menos de 15 minutos (Paso a paso)

### Paso 1: Clonar el repositorio
```bash
git clone https://github.com/Winan03/pillapago.git
cd pillapago
```

### Paso 2: Configurar las variables de entorno
Copia la plantilla de variables de entorno:
```bash
# En Windows (PowerShell / CMD):
copy .env.example .env

# En Linux / macOS / Bash:
cp .env.example .env
```
El archivo `.env` ya viene precargado con valores por defecto listos para desarrollo local.

### Paso 3: Levantar los contenedores locales (PostgreSQL & Redis)
Desde la raíz del proyecto, ejecuta:
```bash
podman compose up -d
```
*(Si usas Docker Desktop, el comando `docker compose up -d` es equivalente).*

Esto iniciará los servicios con salud monitoreada:
- **PostgreSQL 16** en `127.0.0.1:5433` (BD: `pillapago`, usuario: `pillapago_user`, contraseña: `pillapago_pass`)
- **Redis 7** en `127.0.0.1:6379` (contraseña: `redis_pass`, sin persistencia en disco según RNF-30)

Verifica que estén saludables con:
```bash
podman ps
```

### Paso 4: Iniciar la aplicación backend
Ingresa a la carpeta `backend` y ejecuta la aplicación usando el Maven Wrapper:

* **En Windows:**
  ```powershell
  cd backend
  .\mvnw.cmd spring-boot:run
  ```
* **En Linux / macOS:**
  ```bash
  cd backend
  ./mvnw spring-boot:run
  ```

> Al iniciar, **Flyway aplicará automáticamente las migraciones** de base de datos (`V1__initial_schema.sql`), creando las tablas iniciales (`merchants`, `users`, `api_keys`).

### Paso 5: Verificar que la aplicación esté activa (Health Check)
En otra terminal o en tu navegador, comprueba el endpoint de salud de Actuator:
```bash
curl http://localhost:8080/actuator/health
```
Debe responder:
```json
{"status":"UP","groups":["liveness","readiness"]}
```

### Paso 6: Detener el entorno cuando termines de trabajar
* **Para detener la aplicación Spring Boot:**  
  En la terminal donde corre el backend, presiona `Ctrl + C`. La aplicación realizará un apagado controlado (*graceful shutdown*).
* **Para detener los contenedores (PostgreSQL & Redis):**  
  Desde la raíz del proyecto, ejecuta:
  ```bash
  podman compose down
  ```
  *(O puedes pausarlos o eliminarlos directamente desde la interfaz gráfica de Podman Desktop).*

---

## 📁 Estructura del repositorio

```
pillapago/
├── compose.yaml                # Podman Compose (PostgreSQL en 5433, Redis en 6379)
├── .env.example              # Plantilla de variables de entorno (sin credenciales reales)
├── .gitignore                # Ignora .env, build artifacts y carpetas de IDE
├── backend/                  # Módulo Spring Boot 3 + Java 21
│   ├── mvnw / mvnw.cmd       # Maven Wrapper (portabilidad sin instalar Maven)
│   ├── pom.xml               # Dependencias de Spring Boot, Flyway y Testcontainers
│   ├── src/main/java/com/pillapago/main/PillapagoApplication.java
│   ├── src/main/resources/application.yml
│   ├── src/main/resources/db/migration/V1__initial_schema.sql
│   └── src/test/             # Pruebas unitarias y de integración
├── .github/workflows/ci.yml  # Pipeline CI/CD con GitHub Actions
└── docs/                     # Especificaciones y arquitectura
```

---

## 🔧 Consideraciones para Windows con Podman Rootless

1. **Puerto 5433 para PostgreSQL:** Se seleccionó el puerto `5433` hacia el host para evitar colisiones con posibles instalaciones locales de PostgreSQL en Windows que suelen ocupar el puerto `5432`.
2. **Modo Rootless y Redes:** Los puertos están vinculados estrictamente a `127.0.0.1` respetando el principio de mínimo privilegio (RNF-40).
3. **Almacenamiento de WSL:** En entornos Windows con poco espacio en el disco C:, se recomienda alojar las distros de WSL/Podman en discos secundarios (como D:\WSL).

### Levantamiento manual alternativo con Podman CLI:
Si no se utiliza Compose:
```bash
# PostgreSQL
podman run -d -p 127.0.0.1:5433:5432 -e POSTGRES_DB=pillapago -e POSTGRES_USER=pillapago_user -e POSTGRES_PASSWORD=pillapago_pass --name pillapago-postgres docker.io/library/postgres:16-alpine

# Redis
podman run -d -p 127.0.0.1:6379:6379 --name pillapago-redis docker.io/library/redis:7-alpine redis-server --save "" --appendonly no --requirepass redis_pass
```

---

## 🛠️ Comandos útiles con Maven Wrapper

Desde la carpeta `backend/`:

| Tarea | Windows | Linux / macOS |
|---|---|---|
| **Compilar** | `.\mvnw.cmd compile` | `./mvnw compile` |
| **Ejecutar pruebas** | `.\mvnw.cmd test` | `./mvnw test` |
| **Empaquetar y verificar (CI local)** | `.\mvnw.cmd clean verify` | `./mvnw clean verify` |
| **Arrancar la aplicación** | `.\mvnw.cmd spring-boot:run` | `./mvnw spring-boot:run` |