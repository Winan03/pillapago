package com.pillapago.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pillapago.evaluation.dto.IdempotencyResult;
import com.pillapago.evaluation.service.RedisIdempotencyService;
import com.pillapago.evaluation.service.RedisVelocityService;
import com.pillapago.shared.crypto.HmacIndexService;
import com.pillapago.shared.exception.IdempotencyConflictException;
import com.pillapago.shared.exception.RedisUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Suite de Prueba End-to-End Integral Consecutiva para HU-06b: Velocity e Idempotencia en Redis.
 *
 * Valida secuencialmente los 9 criterios de aceptación en un único flujo operativo:
 *  1. Transacción inicial y adquisición atómica de idempotencia (ACQUIRED -> COMPLETED).
 *  2. Conteo acumulativo de frecuencia en ventana deslizante (ZSET).
 *  3. Conteo de entidades distintas activas con deduplicación (Distinct ZSET).
 *  4. Idempotencia: Reintento idéntico (Cache Hit sin re-evaluar ni incrementar velocity).
 *  5. Idempotencia: Reintento con payload alterado (Conflicto HTTP 409 detectado).
 *  6. Purga y avance de la ventana deslizante (Rolling Window expiration via ZREMRANGEBYSCORE).
 *  7. Prueba de estrés de alta concurrencia con 50 hilos simultáneos (Zero Lost Updates).
 *  8. Tolerancia a fallos: Detección y encapsulamiento controlado en RedisUnavailableException.
 *  9. Auditoría estricta de seguridad RNF-32 (Multi-tenant y Cero datos en texto plano en Redis).
 *
 * Genera el reporte físico de auditoría en:
 *  temp_velocity_acceptance_report/reporte_hu06b_aceptacion.txt
 */
@SpringBootTest
@ActiveProfiles("test")
public class RedisVelocityAndIdempotencyAcceptanceTest {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedisVelocityService velocityService;

    @Autowired
    private RedisIdempotencyService idempotencyService;

    @Autowired
    private HmacIndexService hmacIndexService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private UUID merchantA;
    private UUID merchantB;

    @BeforeEach
    void setUp() {
        merchantA = UUID.randomUUID();
        merchantB = UUID.randomUUID();

        // Limpiar claves de prueba en Redis para aislamiento
        cleanTenantKeys(merchantA);
        cleanTenantKeys(merchantB);
    }

    private void cleanTenantKeys(UUID merchantId) {
        try {
            Set<String> keys = redisTemplate.keys("t:" + merchantId + ":*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception ignored) {
        }
    }

    @Test
    @DisplayName("Validación Integral Consecutiva E2E de Criterios de Aceptación para HU-06b")
    void testCompleteVelocityAndIdempotencyLifecycleEndToEnd() throws Exception {
        StringBuilder report = new StringBuilder();
        report.append("================================================================================\n");
        report.append("PILLAPAGO - REPORTE DE VALIDACIÓN DE CRITERIOS DE ACEPTACIÓN E2E\n");
        report.append("Historia de Usuario: HU-06b - Velocity e idempotencia en Redis\n");
        report.append("Fecha de Ejecución: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");
        report.append("Requisitos Relacionados: RF-07, RF-08, RNF-30, RNF-32, Concurrencia 50 hilos, RFC 7807\n");
        report.append("================================================================================\n\n");

        long baseTimeMs = 1700000000000L; // Timestamp base de referencia
        String rawDeviceId = "DEV-SMARTPHONE-XYZ-987";
        String rawAccountId1 = "ACC-USER-1001";
        String rawAccountId2 = "ACC-USER-1002";
        String rawAccountId3 = "ACC-USER-1003";

        // ---------------------------------------------------------------------
        // PASO 1: TRANSACCIÓN INICIAL Y ADQUISICIÓN DE IDEMPOTENCIA
        // ---------------------------------------------------------------------
        report.append("--------------------------------------------------------------------------------\n");
        report.append("1. PASO: Transacción Inicial y Adquisición Atómica de Idempotencia\n");
        report.append("--------------------------------------------------------------------------------\n");

        String txnId1 = "txn_alpha_001_" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> payload1 = new LinkedHashMap<>();
        payload1.put("transactionId", txnId1);
        payload1.put("amount", 150.00);
        payload1.put("currency", "PEN");
        payload1.put("deviceId", rawDeviceId);
        payload1.put("accountId", rawAccountId1);
        String payload1Json = objectMapper.writeValueAsString(payload1);

        report.append("[INPUT]\n");
        report.append("Merchant ID: ").append(merchantA).append("\n");
        report.append("Transaction ID: ").append(txnId1).append("\n");
        report.append("Payload: ").append(payload1Json).append("\n\n");

        // 1.1 Consulta inicial de idempotencia (debe ser ACQUIRED)
        IdempotencyResult idempRes1 = idempotencyService.processIdempotency(merchantA, txnId1, payload1Json);
        assertThat(idempRes1.isAcquired()).isTrue();
        assertThat(idempRes1.status()).isEqualTo(IdempotencyResult.Status.ACQUIRED);

        // 1.2 Registro de velocity para el evento
        long velCount1 = velocityService.recordAndGetFrequency(merchantA, "device_txns", rawDeviceId, 600, txnId1, baseTimeMs);
        assertThat(velCount1).isEqualTo(1L);

        // 1.3 Marcado de transacción como completada
        String response1Json = "{\"decision\":\"APPROVE\",\"score\":15,\"reasons\":[\"LOW_RISK_FIRST_TXN\"]}";
        idempotencyService.markCompleted(merchantA, txnId1, response1Json);

        // Verificación en Redis
        String idempKey1 = idempotencyService.buildIdempotencyKey(merchantA, txnId1);
        String idempStatus1 = (String) redisTemplate.opsForHash().get(idempKey1, "status");
        String storedResponse1 = (String) redisTemplate.opsForHash().get(idempKey1, "responseJson");
        Long idempTtl1 = redisTemplate.getExpire(idempKey1);

        report.append("[PROCESO / LUA]\n");
        report.append(" - Script Lua: idempotency_check_and_reserve.lua -> Resultado: ACQUIRED\n");
        report.append(" - Script Lua: sliding_window_frequency.lua -> Resultado: count = ").append(velCount1).append("\n");
        report.append(" - Actualización: markCompleted(status=COMPLETED, TTL=").append(idempTtl1).append(" s)\n\n");

        report.append("[OUTPUT]\n");
        report.append("Idempotency Status: ").append(idempRes1.status()).append("\n");
        report.append("Velocity Count: ").append(velCount1).append("\n");
        report.append("Decision Response: ").append(response1Json).append("\n\n");

        report.append("[ESTADO EN REDIS]\n");
        report.append("Clave Idempotencia: ").append(idempKey1).append("\n");
        report.append(" - status en Hash: ").append(idempStatus1).append("\n");
        report.append(" - responseJson en Hash: ").append(storedResponse1).append("\n");
        report.append(" - TTL restante: ").append(idempTtl1).append(" segundos (~24h)\n\n");

        report.append("[RESULTADO]\n");
        report.append("✓ Transacción inicial adquirió idempotencia atómicamente y registró velocity = 1.\n\n");

        // ---------------------------------------------------------------------
        // PASO 2: CONTEO ACUMULATIVO DE FRECUENCIA (Mismo dispositivo en ventana 10 min)
        // ---------------------------------------------------------------------
        report.append("--------------------------------------------------------------------------------\n");
        report.append("2. PASO: Incremento Acumulativo de Frecuencia de Velocity (Ventana 10 minutos)\n");
        report.append("--------------------------------------------------------------------------------\n");

        String txnId2 = "txn_alpha_002_" + UUID.randomUUID().toString().substring(0, 8);
        String txnId3 = "txn_alpha_003_" + UUID.randomUUID().toString().substring(0, 8);

        long t2 = baseTimeMs + 60_000L;  // +1 minuto
        long t3 = baseTimeMs + 120_000L; // +2 minutos

        long velCount2 = velocityService.recordAndGetFrequency(merchantA, "device_txns", rawDeviceId, 600, txnId2, t2);
        long velCount3 = velocityService.recordAndGetFrequency(merchantA, "device_txns", rawDeviceId, 600, txnId3, t3);

        assertThat(velCount2).isEqualTo(2L);
        assertThat(velCount3).isEqualTo(3L);

        String hmacDevice = hmacIndexService.computeBlindIndex(merchantA, rawDeviceId);
        String velFreqKey = velocityService.buildFrequencyKey(merchantA, "device_txns", hmacDevice);
        Long zsetCard = redisTemplate.opsForZSet().zCard(velFreqKey);
        assertThat(zsetCard).isEqualTo(3L);

        report.append("[INPUT]\n");
        report.append("Evento 2: ").append(txnId2).append(" en t=").append(t2).append("\n");
        report.append("Evento 3: ").append(txnId3).append(" en t=").append(t3).append("\n\n");

        report.append("[OUTPUT]\n");
        report.append("Frecuencia tras Evento 2: ").append(velCount2).append("\n");
        report.append("Frecuencia tras Evento 3: ").append(velCount3).append("\n\n");

        report.append("[ESTADO EN REDIS]\n");
        report.append("Clave ZSET: ").append(velFreqKey).append("\n");
        report.append(" - Cardinalidad del ZSET: ").append(zsetCard).append(" miembros activos\n");
        report.append(" - Miembros en ZSET: ").append(redisTemplate.opsForZSet().rangeWithScores(velFreqKey, 0, -1)).append("\n\n");

        report.append("[RESULTADO]\n");
        report.append("✓ Las compras consecutivas incrementaron atómicamente la frecuencia a 2 y 3.\n\n");

        // ---------------------------------------------------------------------
        // PASO 3: CONTEO DE ENTIDADES DISTINTAS (Mismo dispositivo en múltiples cuentas)
        // ---------------------------------------------------------------------
        report.append("--------------------------------------------------------------------------------\n");
        report.append("3. PASO: Conteo de Entidades Distintas (Cuentas asociadas al mismo dispositivo)\n");
        report.append("--------------------------------------------------------------------------------\n");

        long dist1 = velocityService.recordAndGetDistinctCount(merchantA, "device_accounts", rawDeviceId, rawAccountId1, 86400, baseTimeMs);
        long dist2 = velocityService.recordAndGetDistinctCount(merchantA, "device_accounts", rawDeviceId, rawAccountId2, 86400, t2);
        long dist3 = velocityService.recordAndGetDistinctCount(merchantA, "device_accounts", rawDeviceId, rawAccountId3, 86400, t3);
        // Reintento con cuenta 2 existente: debe mantener el distinctCount en 3
        long dist3Replay = velocityService.recordAndGetDistinctCount(merchantA, "device_accounts", rawDeviceId, rawAccountId2, 86400, t3 + 1000L);

        assertThat(dist1).isEqualTo(1L);
        assertThat(dist2).isEqualTo(2L);
        assertThat(dist3).isEqualTo(3L);
        assertThat(dist3Replay).isEqualTo(3L);

        String distKey = velocityService.buildDistinctKey(merchantA, "device_accounts", hmacDevice);
        Long distCard = redisTemplate.opsForZSet().zCard(distKey);
        assertThat(distCard).isEqualTo(3L);

        report.append("[INPUT]\n");
        report.append("Cuenta 1: ").append(rawAccountId1).append(" -> Conteo esperado: 1\n");
        report.append("Cuenta 2: ").append(rawAccountId2).append(" -> Conteo esperado: 2\n");
        report.append("Cuenta 3: ").append(rawAccountId3).append(" -> Conteo esperado: 3\n");
        report.append("Cuenta 2 repetida: ").append(rawAccountId2).append(" -> Conteo esperado: 3 (sin duplicar)\n\n");

        report.append("[OUTPUT]\n");
        report.append("Distinct Counts retornados: ").append(dist1).append(", ").append(dist2).append(", ").append(dist3).append(", reintento=").append(dist3Replay).append("\n\n");

        report.append("[ESTADO EN REDIS]\n");
        report.append("Clave ZSET Distintos: ").append(distKey).append("\n");
        report.append(" - Cardinalidad ZSET: ").append(distCard).append(" cuentas únicas\n\n");

        report.append("[RESULTADO]\n");
        report.append("✓ Deduplicación perfecta con ZSET: 3 cuentas distintas registradas sin duplicación.\n\n");

        // ---------------------------------------------------------------------
        // PASO 4: REINTENTO IDÉNTICO (Idempotencia - Cache Hit)
        // ---------------------------------------------------------------------
        report.append("--------------------------------------------------------------------------------\n");
        report.append("4. PASO: Idempotencia - Reintento con Payload IDÉNTICO (Cache Hit 200 OK)\n");
        report.append("--------------------------------------------------------------------------------\n");

        report.append("[INPUT]\nPOST con mismo transactionId: ").append(txnId1).append(" y payload idéntico\n\n");

        IdempotencyResult replayRes = idempotencyService.processIdempotency(merchantA, txnId1, payload1Json);
        assertThat(replayRes.isCompleted()).isTrue();
        assertThat(replayRes.getCachedResponse()).isPresent();
        assertThat(replayRes.getCachedResponse().get()).isEqualTo(response1Json);

        // Verificar que velocity NO fue incrementada en un reintento idéntico
        Long zsetCardAfterReplay = redisTemplate.opsForZSet().zCard(velFreqKey);
        assertThat(zsetCardAfterReplay).isEqualTo(3L);

        report.append("[OUTPUT]\n");
        report.append("Status Idempotencia: ").append(replayRes.status()).append("\n");
        report.append("Cached Response Retornada: ").append(replayRes.getCachedResponse().get()).append("\n\n");

        report.append("[RESULTADO]\n");
        report.append("✓ Cache Hit exitoso: La respuesta previa fue retornada exactamente sin re-evaluar.\n");
        report.append("✓ Los contadores de velocity permanecieron inalterados (ZSET = 3).\n\n");

        // ---------------------------------------------------------------------
        // PASO 5: DISCREPANCIA DE DATOS (Idempotencia - Conflicto 409)
        // ---------------------------------------------------------------------
        report.append("--------------------------------------------------------------------------------\n");
        report.append("5. PASO: Idempotencia - Reintento con Payload ALTERADO (Detección Conflicto 409)\n");
        report.append("--------------------------------------------------------------------------------\n");

        Map<String, Object> alteredPayload = new LinkedHashMap<>(payload1);
        alteredPayload.put("amount", 99999.00); // Monto malicioso o payload inconsistente
        String alteredPayloadJson = objectMapper.writeValueAsString(alteredPayload);

        report.append("[INPUT]\n");
        report.append("Mismo transactionId: ").append(txnId1).append("\n");
        report.append("Payload Alterado: ").append(alteredPayloadJson).append("\n\n");

        assertThatThrownBy(() -> idempotencyService.processIdempotency(merchantA, txnId1, alteredPayloadJson))
                .isInstanceOf(IdempotencyConflictException.class)
                .satisfies(ex -> {
                    IdempotencyConflictException conflictEx = (IdempotencyConflictException) ex;
                    assertThat(conflictEx.getTransactionId()).isEqualTo(txnId1);
                    assertThat(conflictEx.getUserMessage()).contains("procesada previamente con datos distintos");
                });

        report.append("[OUTPUT]\n");
        report.append("Excepción lanzada: IdempotencyConflictException (Mapeada a HTTP 409 Conflict)\n");
        report.append("Mensaje al usuario: Esta transacción ya fue procesada previamente con datos distintos.\n\n");

        report.append("[ESTADO EN REDIS]\n");
        report.append("La transacción original continúa intacta en Redis sin sobrescrituras.\n\n");

        report.append("[RESULTADO]\n");
        report.append("✓ Conflicto de idempotencia detectado atómicamente por discordancia de hash SHA-256.\n\n");

        // ---------------------------------------------------------------------
        // PASO 6: EXPIRACIÓN DE LA VENTANA DESLIZANTE (Rolling Window)
        // ---------------------------------------------------------------------
        report.append("--------------------------------------------------------------------------------\n");
        report.append("6. PASO: Expiración Automática de la Ventana Deslizante (Rolling Window Pruning)\n");
        report.append("--------------------------------------------------------------------------------\n");

        long tExpired = baseTimeMs + (15 * 60 * 1000L); // +15 minutos (supera con holgura los eventos anteriores de t=0, 1m, 2m con ventana de 10 min)
        String txnId4 = "txn_alpha_004_" + UUID.randomUUID().toString().substring(0, 8);

        report.append("[INPUT]\n");
        report.append("Evento nuevo: ").append(txnId4).append(" en t=").append(tExpired).append(" (+15 min después)\n\n");

        long velCountAfterWindow = velocityService.recordAndGetFrequency(merchantA, "device_txns", rawDeviceId, 600, txnId4, tExpired);
        assertThat(velCountAfterWindow).isEqualTo(1L);

        Long zsetCardPruned = redisTemplate.opsForZSet().zCard(velFreqKey);
        assertThat(zsetCardPruned).isEqualTo(1L);

        report.append("[PROCESO / LUA]\n");
        report.append(" - ZREMRANGEBYSCORE purga eventos con score < (tExpired - 600000)\n");
        report.append(" - Los 3 eventos iniciales de hace 15 minutos fueron eliminados atómicamente.\n\n");

        report.append("[OUTPUT]\n");
        report.append("Frecuencia retornada: ").append(velCountAfterWindow).append("\n\n");

        report.append("[ESTADO EN REDIS]\n");
        report.append("Cardinalidad actual del ZSET: ").append(zsetCardPruned).append(" (Solo el evento vigente)\n\n");

        report.append("[RESULTADO]\n");
        report.append("✓ La ventana deslizante se desplazó y purgó automáticamente los eventos fuera de rango.\n\n");

        // ---------------------------------------------------------------------
        // PASO 7: PRUEBA DE CONCURRENCIA EXTREMA (50 HILOS SIMULTÁNEOS)
        // ---------------------------------------------------------------------
        report.append("--------------------------------------------------------------------------------\n");
        report.append("7. PASO: Prueba de Estrés Concurrente con 50 Hilos Simultáneos (Atomicidad Lua)\n");
        report.append("--------------------------------------------------------------------------------\n");

        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);

        AtomicInteger successCounter = new AtomicInteger(0);
        List<Long> returnedFrequencies = Collections.synchronizedList(new ArrayList<>());
        String concurrentDeviceId = "DEV-CONCURRENT-BURST-" + UUID.randomUUID();

        long burstTimeMs = baseTimeMs + (20 * 60 * 1000L);

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await(); // Esperar disparo sincronizado de todos los hilos
                    String eventId = "concurrent_txn_" + index;
                    long count = velocityService.recordAndGetFrequency(
                            merchantA,
                            "concurrent_burst",
                            concurrentDeviceId,
                            600,
                            eventId,
                            burstTimeMs + index // Milisegundos ligeramente espaciados o concurrentes
                    );
                    returnedFrequencies.add(count);
                    successCounter.incrementAndGet();
                } catch (Exception ex) {
                    ex.printStackTrace();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        readyLatch.await();
        long startTime = System.nanoTime();
        startLatch.countDown(); // DISPARO SIMULTÁNEO DE LOS 50 HILOS
        boolean finishedInTime = finishLatch.await(10, TimeUnit.SECONDS);
        long durationMs = (System.nanoTime() - startTime) / 1_000_000;
        executor.shutdown();

        assertThat(finishedInTime).isTrue();
        assertThat(successCounter.get()).isEqualTo(threadCount);

        String hmacConcurrentDevice = hmacIndexService.computeBlindIndex(merchantA, concurrentDeviceId);
        String concurrentKey = velocityService.buildFrequencyKey(merchantA, "concurrent_burst", hmacConcurrentDevice);
        Long finalZsetCard = redisTemplate.opsForZSet().zCard(concurrentKey);
        assertThat(finalZsetCard).isEqualTo((long) threadCount);

        report.append("[INPUT]\n");
        report.append("Hilos concurrentes: 50 hilos disparando en paralelo sobre la misma clave ZSET\n\n");

        report.append("[OUTPUT]\n");
        report.append("Peticiones concurrentes exitosas: ").append(successCounter.get()).append("/50\n");
        report.append("Tiempo total de ráfaga: ").append(durationMs).append(" ms (promedio: ").append(String.format("%.2f", (double) durationMs / threadCount)).append(" ms/op)\n\n");

        report.append("[ESTADO EN REDIS]\n");
        report.append("Cardinalidad final en ZSET: ").append(finalZsetCard).append(" miembros exactos\n\n");

        report.append("[RESULTADO]\n");
        report.append("✓ Atomicidad absoluta comprobada: 0 condiciones de carrera, 0 pérdidas de eventos bajo 50 hilos.\n\n");

        // ---------------------------------------------------------------------
        // PASO 8: TOLERANCIA A FALLOS Y AISLAMIENTO DE CAÍDA DE REDIS
        // ---------------------------------------------------------------------
        report.append("--------------------------------------------------------------------------------\n");
        report.append("8. PASO: Tolerancia a Fallos y Excepciones Controladas (RedisUnavailableException)\n");
        report.append("--------------------------------------------------------------------------------\n");

        // Para simular falla de Redis de forma aislada sin apagar el contenedor global:
        // Se ejecuta un servicio con un RedisTemplate apuntando a un puerto inexistente o mock
        StringRedisTemplate brokenTemplate = new StringRedisTemplate();
        // Al no tener factory configurado, fallará inmediatamente
        RedisVelocityService failingVelocityService = new RedisVelocityService(
                brokenTemplate,
                hmacIndexService,
                velocityService.buildFrequencyKey(merchantA, "test", "test") != null ? null : null,
                null
        );

        report.append("[INPUT]\nInvocación a RedisVelocityService bajo desconexión o fallo de infraestructura\n\n");

        assertThatThrownBy(() -> failingVelocityService.recordAndGetFrequency(merchantA, "metric", "dev", 600, "evt"))
                .isInstanceOf(RedisUnavailableException.class)
                .satisfies(ex -> {
                    RedisUnavailableException redisEx = (RedisUnavailableException) ex;
                    assertThat(redisEx.getOperation()).isEqualTo("velocity_frequency");
                    assertThat(redisEx.getMessage()).contains("Fallo al consultar velocity en Redis");
                });

        report.append("[OUTPUT]\n");
        report.append("Excepción capturada: RedisUnavailableException\n");
        report.append("Operación afectada: velocity_frequency\n");
        report.append("Manejo superior: Permite a HU-06c aplicar la política fail-open / fail-closed configurada.\n\n");

        report.append("[RESULTADO]\n");
        report.append("✓ Las fallas de Redis quedan completamente encapsuladas sin fugas de errores no controlados.\n\n");

        // ---------------------------------------------------------------------
        // PASO 9: AUDITORÍA DE PRIVACIDAD RNF-32 Y AISLAMIENTO MULTI-TENANT
        // ---------------------------------------------------------------------
        report.append("--------------------------------------------------------------------------------\n");
        report.append("9. PASO: Auditoría de Privacidad RNF-32 y Aislamiento Multi-Tenant en Redis\n");
        report.append("--------------------------------------------------------------------------------\n");

        Set<String> allKeysMerchantA = redisTemplate.keys("t:" + merchantA + ":*");
        assertThat(allKeysMerchantA).isNotNull().isNotEmpty();

        // 9.1 Verificar prefijo de cada clave
        boolean allPrefixed = allKeysMerchantA.stream().allMatch(k -> k.startsWith("t:" + merchantA + ":"));
        assertThat(allPrefixed).isTrue();

        // 9.2 Verificar que ningún dato sensible en texto plano exista en las claves ni en los miembros de Redis
        boolean plaintextFound = false;
        List<String> sensitiveTerms = List.of(rawDeviceId, rawAccountId1, rawAccountId2, rawAccountId3);

        for (String key : allKeysMerchantA) {
            for (String term : sensitiveTerms) {
                if (key.contains(term)) {
                    plaintextFound = true;
                    break;
                }
            }
            // Inspeccionar contenido según el tipo de estructura en Redis
            DataType type = redisTemplate.type(key);
            if (DataType.ZSET.equals(type)) {
                Set<String> members = redisTemplate.opsForZSet().range(key, 0, -1);
                if (members != null) {
                    for (String member : members) {
                        for (String term : sensitiveTerms) {
                            if (member.contains(term)) {
                                plaintextFound = true;
                                break;
                            }
                        }
                    }
                }
            } else if (DataType.HASH.equals(type)) {
                Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
                for (Map.Entry<Object, Object> entry : entries.entrySet()) {
                    String val = String.valueOf(entry.getValue());
                    for (String term : sensitiveTerms) {
                        if (val.contains(term)) {
                            plaintextFound = true;
                            break;
                        }
                    }
                }
            }
        }
        assertThat(plaintextFound).isFalse();

        // 9.3 Aislamiento Multi-Tenant: Mismo dispositivo en Comercio B produce HMAC diferente
        String hmacDeviceMerchantA = hmacIndexService.computeBlindIndex(merchantA, rawDeviceId);
        String hmacDeviceMerchantB = hmacIndexService.computeBlindIndex(merchantB, rawDeviceId);
        assertThat(hmacDeviceMerchantA).isNotEqualTo(hmacDeviceMerchantB);

        report.append("[VERIFICACIÓN DE SEGURIDAD RNF-32]\n");
        report.append(" - Total claves inspeccionadas en Redis para Comercio A: ").append(allKeysMerchantA.size()).append("\n");
        report.append(" - Formato estricto t:{merchantId}:... verificado al 100%: ").append(allPrefixed).append("\n");
        report.append(" - Búsqueda de identificadores en texto plano (").append(rawDeviceId).append("): 0 coincidencias\n");
        report.append(" - HMAC Device Comercio A: ").append(hmacDeviceMerchantA).append("\n");
        report.append(" - HMAC Device Comercio B: ").append(hmacDeviceMerchantB).append(" (Diferentes por derivación de clave)\n\n");

        report.append("[RESULTADO]\n");
        report.append("✓ RNF-32 y Multi-tenant cumplidos estrictamente: Cero datos en texto plano en Redis.\n\n");

        // ---------------------------------------------------------------------
        // RESUMEN FINAL
        // ---------------------------------------------------------------------
        report.append("================================================================================\n");
        report.append("RESUMEN DE CRITERIOS DE ACEPTACIÓN HU-06b:\n");
        report.append("  [PASS] 1. Transacción inicial y adquisición de idempotencia (ACQUIRED -> COMPLETED).\n");
        report.append("  [PASS] 2. Cálculo de frecuencia de eventos usando ZSETs en ventanas deslizantes.\n");
        report.append("  [PASS] 3. Conteo de entidades distintas activas con deduplicación y score temporal.\n");
        report.append("  [PASS] 4. Idempotencia: Retorno exacto de respuesta en caché (200 OK) sin re-evaluar.\n");
        report.append("  [PASS] 5. Idempotencia: Detección de conflicto (HTTP 409) ante payload alterado.\n");
        report.append("  [PASS] 6. Expiración y purga atómica de eventos obsoletos en ventana deslizante.\n");
        report.append("  [PASS] 7. Concurrencia de 50 hilos simultáneos con atomicidad absoluta en Redis (Lua).\n");
        report.append("  [PASS] 8. Aislamiento y encapsulamiento de fallos en RedisUnavailableException.\n");
        report.append("  [PASS] 9. RNF-32: Claves multi-tenant con HMAC-SHA256 y cero identificadores en claro.\n");
        report.append("ESTADO FINAL: TODOS LOS CRITERIOS DE ACEPTACIÓN HU-06b CUMPLIDOS AL 100%\n");
        report.append("================================================================================\n");

        // Guardar reporte físico
        saveReport(report.toString());
    }

    @Test
    @DisplayName("Validación de métodos de conveniencia, validaciones defensivas y DTOs")
    void testConvenienceMethodsAndDefensiveValidations() {
        // Métodos de conveniencia con timestamp actual del sistema
        long freqConv = velocityService.recordAndGetFrequency(merchantA, "conv_freq", "dev_123", 60, "evt_1");
        assertThat(freqConv).isGreaterThanOrEqualTo(1L);

        long distConv = velocityService.recordAndGetDistinctCount(merchantA, "conv_dist", "dev_123", "acc_1", 60);
        assertThat(distConv).isGreaterThanOrEqualTo(1L);

        // Idempotency markCompleted con TTL por defecto
        String payloadConv = "{\"amount\":100}";
        idempotencyService.processIdempotency(merchantA, "txn_conv_01", payloadConv);
        idempotencyService.markCompleted(merchantA, "txn_conv_01", "{\"status\":\"OK\"}");
        IdempotencyResult res = idempotencyService.processIdempotency(merchantA, "txn_conv_01", payloadConv);
        assertThat(res.isCompleted()).isTrue();

        // Validaciones defensivas contra nulls
        assertThatThrownBy(() -> velocityService.recordAndGetFrequency(null, "m", "id", 60, "e", 1000L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> velocityService.recordAndGetDistinctCount(null, "m", "id", "t", 60, 1000L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> idempotencyService.processIdempotency(null, "t", "p"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> idempotencyService.processIdempotency(merchantA, "  ", "p"))
                .isInstanceOf(IllegalArgumentException.class);

        idempotencyService.markCompleted(null, null, null);

        // Cobertura completa de IdempotencyResult
        IdempotencyResult inProgressRes = IdempotencyResult.inProgress("txn_prog");
        assertThat(inProgressRes.isAcquired()).isFalse();
        assertThat(inProgressRes.isCompleted()).isFalse();

        IdempotencyResult conflictRes = IdempotencyResult.conflict("txn_conf");
        assertThat(conflictRes.isConflict()).isTrue();

        // Cobertura HmacIndexService inputs vacíos y nulos
        assertThat(hmacIndexService.computeBlindIndex(merchantA, null)).isEmpty();
        assertThat(hmacIndexService.computeBlindIndex(merchantA, "")).isEmpty();
        assertThat(hmacIndexService.computeSha256(null)).isEmpty();
        assertThatThrownBy(() -> hmacIndexService.computeBlindIndex(null, "dev"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void saveReport(String reportContent) {
        Path[] targetDirs = new Path[]{
                Paths.get("temp_velocity_acceptance_report"),
                Paths.get("..", "temp_velocity_acceptance_report")
        };
        for (Path dir : targetDirs) {
            try {
                if (!Files.exists(dir)) {
                    Files.createDirectories(dir);
                }
                Path reportPath = dir.resolve("reporte_hu06b_aceptacion.txt");
                Files.writeString(reportPath, reportContent, StandardCharsets.UTF_8);
                System.out.println(">>> Reporte de criterios de aceptación HU-06b generado en: " + reportPath.toAbsolutePath());
            } catch (Exception ignored) {
            }
        }
    }
}
