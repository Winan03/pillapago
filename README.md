# Pillapago - Motor de Decisión Antifraude

## 📋 Descripción general
Pillapago es un motor de decisión antifraude en tiempo real para comercios online, construido con **Spring Boot 3 + Java 21**, PostgreSQL y Redis.

## 🚀 Levantamiento en menos de 15 minutos

### Paso 1: Clonar el repositorio
```bash
git clone https://github.com/Winan03/pillapago.git
cd pillapago
```

### Paso 2: Levantar los contenedores locales
```bash
docker compose up -d
```

Esto iniciará:
- **PostgreSQL** en `127.0.0.1:5432` (base de datos: `pillapago`, user: `pillapago_user`, password: `pillapago_pass`)
- **Redis** en `127.0.0.1:6379` (password: `redis_pass`)

> **Nota importante para Windows:** Los puertos solo están expuestos en `127.0.0.1` para evitar conflictos. Si el demonio de Docker no arranca, ejecuta Docker Desktop como administrador y verifica que la virtualización esté habilitada en la BIOS.

### Paso 3: Configurar variables de entorno
Copiar el archivo de ejemplo y ajustar si es necesario:
```bash
copy .env.example .env
```
El archivo `.env` ya viene con valores por defecto que funcionan con la configuración de Docker Compose. No modifique las contraseñas a menos que cambie también el `compose.yaml`.

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
├── compose.yaml          # Docker Compose con PostgreSQL y Redis
├── .env.example        # Variables de entorno (sin secretos reales)
├── backend/            # Módulo Spring Boot 3 + Java 21
│   ├── pom.xml         # Dependencias Maven
│   ├── src/main/java/com/pillapago/main/PillapagoApplication.java
│   ├── src/main/resources/application.yml
│   └── src/main/resources/db/migration/V1__initial_schema.sql
├── .github/workflows/ci.yml  # GitHub Actions CI/CD
└── docs/               # Documentación adicional
```

## 🔧 Consideraciones para Windows

### Problemas comunes y soluciones:

1. **Puertos bloqueados:** Los contenedores publican solo en `127.0.0.1`. Si otro servicio usa el puerto 5432 o 6379, edite `compose.yaml` para cambiar los puertos.

2. **Demonio de Docker:** Asegúrese de que Docker Desktop esté corriendo. En Windows, puede necesitar reiniciar el servicio:
   ```powershell
   Restart-Service docker
   ```

3. **Configuración de red:** El archivo `compose.yaml` usa redes puente con `127.0.0.1` explícito. Si usa Podman en lugar de Docker, los mismos archivos son compatibles.

4. **Java 21 + Testcontainers:** El `pom.xml` incluye configuración para Testcontainers con GC serial (`-XX:+UseSerialGC`) para mantener el consumo de memoria ≤ 512MB según los requisitos no funcionales.

## 🛠️ Scripts disponibles

| Comando | Descripción |
|---------|-------------|
| `mvn compile` | Compilar el proyecto |
| `mvn flyway:migrate` | Ejecutar migraciones Flyway |
| `mvn verify` | Ejecutar todas las pruebas (usa Testcontainers) |
| `mvn jacoco:report` | Generar reporte de cobertura |
| `docker compose down` | Detener y remover contenedores |
| `docker compose up -d` | Levantar contenedores en background |

## 📦 Tecnologías utilizadas

- **Java 21** + **Spring Boot 3.2.4**
- **PostgreSQL 16** + **Flyway** para migraciones
- **Redis 7** para caché y velocity
- **Testcontainers** para pruebas integradas
- **GitHub Actions** para CI/CD
- **Docker Compose** para entorno local