# Pillapago - Motor de Decisión Antifraude

## 📋 Descripción general
Pillapago es un motor de decisión antifraude en tiempo real para comercios online, construido con **Spring Boot 3 + Java 21**, PostgreSQL y **Podman**.

## 🚀 Levantamiento en menos de 15 minutos

### Paso 1: Clonar el repositorio
```bash
git clone https://github.com/Winan03/pillapago.git
cd pillapago
```

### Paso 2: Levantar los contenedores locales (Podman)
```bash
podman compose up -d
```
*Nota: Podman Desktop incluye compatibilidad con `docker compose` commands. Si el comando no funciona, use `podman compose up -d` directamente.*

Esto iniciará:
- **PostgreSQL** en `127.0.0.1:5432` (base de datos: `pillapago`, user: `pillapago_user`, password: `pillapago_pass`)
- **Redis** en `127.0.0.1:6379` (password: `redis_pass`)

> **Importante para Windows/Podman rootless (RNF-40):** Los puertos solo están expuestos en `127.0.0.1`. Podman se ejecuta en modo rootless por defecto, lo que significa que el contenedor se ejecuta con el usuario actual sin privilegios de administrador. Si experimenta problemas de resolución de red, verifique que el adaptador de red tenga configurado IPv4 en 127.0.0.1.

### Paso 3: Configurar variables de entorno
Copiar el archivo de ejemplo y ajustar si es necesario:
```bash
copy .env.example .env
```
El archivo `.env` ya viene con valores por defecto que funcionan con la configuración de Podman Compose. No modifique las contraseñas a menos que cambie también el `compose.yaml`.

### Paso 4: Verificar que la aplicación arranque
Con los contenedores corriendo, inicie el backend:
```bash
cd backend
mvn spring-boot:run
```

Luego verifique el health endpoint:
```bash
curl http://localhost:8080/actuator/health
```
Debería responder con `"status":"UP"`.

### Paso 5: Ejecutar migraciones Flyway
Las migraciones se aplican automáticamente al iniciar la aplicación. Para ejecutarlas manualmente:
```bash
mvn flyway:migrate
```

## 📁 Estructura del repositorio

```
pillapago/
├── compose.yaml          # Podman Compose con PostgreSQL y Redis (rootless)
├── .env.example        # Variables de entorno (sin secretos reales)
├── backend/            # Módulo Spring Boot 3 + Java 21
│   ├── pom.xml         # Dependencias Maven
│   ├── src/main/java/com/pillapago/main/PillapagoApplication.java
│   ├── src/main/resources/application.yml
│   └── src/main/resources/db/migration/V1__initial_schema.sql
├── .github/workflows/ci.yml  # GitHub Actions CI/CD
└── docs/               # Documentación adicional
```

## 🔧 Consideraciones para Windows con Podman Rootless

### Problemas comunes y soluciones:

1. **Puertos bloqueados:** Los contenedores publican solo en `127.0.0.1`. Si otro servicio usa el puerto 5432 o 6379, edite `compose.yaml` para cambiar los puertos.

2. **Modo rootless:** Podman se ejecuta por defecto sin privilegios de administrador. Si necesita contenedores con privilegios, inicie Podman Desktop con permisos elevados o cambie la configuración.

3. **Configuración de red:** El archivo `compose.yaml` usa redes puente con `127.0.0.1` explícito. La especificación RNF-40 requiere puertos publicados solo en 127.0.0.1 para aislamiento mínimo privilegio.

4. **Java 21 + Testcontainers:** El `pom.xml` incluye configuración para Testcontainers con GC serial (`-XX:+UseSerialGC`) para mantener el consumo de memoria ≤ 512MB según los requisitos no funcionales.

5. **Volúmenes:** El `compose.yaml` actual no usa volúmenes nombrados para compatibilidad rootless. Si necesita persistencia, use directorios locales en Windows.

### Levantamiento alternativo con Podman CLI:

Si `podman compose` no está disponible, use:

```bash
# Levantar PostgreSQL manualmente
podman run -d -p 127.0.0.1:5432:5432 -e POSTGRES_DB=pillapago -e POSTGRES_USER=pillapago_user -e POSTGRES_PASSWORD=pillapago_pass --name pillapago-postgres docker.io/library/postgres:16-alpine

# Levantar Redis manualmente
podman run -d -p 127.0.0.1:6379:6379 --name pillapago-redis docker.io/library/redis:7-alpine --save "" --append-only "no"
```

## 🛠️ Scripts disponibles

| Comando | Descripción |
|---------|-------------|
| `mvn compile` | Compilar el proyecto |
| `mvn flyway:migrate` | Ejecutar migraciones Flyway |
| `mvn verify` | Ejecutar todas las pruebas (usa Testcontainers) |
| `mvn jacoco:report` | Generar reporte de cobertura |
| `podman compose down` | Detener y remover contenedores |
| `podman compose up -d` | Levantar contenedores en background |

## 📦 Tecnologías utilizadas

- **Java 21** + **Spring Boot 3.2.4**
- **PostgreSQL 16** + **Flyway** para migraciones
- **Redis 7** para caché y velocity
- **Testcontainers** para pruebas integradas
- **GitHub Actions** para CI/CD
- **Podman Compose** para entorno local (rootless por defecto)

### Consideraciones de seguridad (RNF-40 Mínimo privilegio):
- Contenedores rootless: el usuario de la aplicación no tiene privilegios de DDL en la BD
- Puertos solo en 127.0.0.1: ningún acceso externo no autorizado
- Sin volúmenes persistentes en disco para Redis (según RNF-30: "Redis sin persistencia en disco")
- Islas de red por tenant via `merchant_id` en todas las consultas