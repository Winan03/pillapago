package com.pillapago.evaluation.service;

import com.pillapago.evaluation.dto.IdempotencyResult;
import com.pillapago.shared.crypto.HmacIndexService;
import com.pillapago.shared.exception.IdempotencyConflictException;
import com.pillapago.shared.exception.RedisUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * Servicio de Idempotencia basado en Redis (RF-08).
 *
 * Garantiza que:
 * 1. Peticiones repetidas con el mismo transactionId dentro de 24 horas devuelvan exactamente
 *    la respuesta previa en caché sin re-evaluar reglas ni duplicar contadores.
 * 2. Peticiones con el mismo transactionId pero diferente payload generen un conflicto (HTTP 409).
 * 3. Las operaciones de reserva y consulta se ejecuten atómicamente con Lua Script.
 */
@Service
public class RedisIdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(RedisIdempotencyService.class);

    public static final long DEFAULT_IDEMPOTENCY_TTL_SECONDS = 86400L; // 24 horas
    private static final String IDEMP_KEY_PREFIX = "t:%s:idemp:%s";

    private final StringRedisTemplate redisTemplate;
    private final HmacIndexService hmacIndexService;
    private final RedisScript<String> idempotencyScript;

    public RedisIdempotencyService(
            StringRedisTemplate redisTemplate,
            HmacIndexService hmacIndexService,
            RedisScript<String> idempotencyScript) {
        this.redisTemplate = redisTemplate;
        this.hmacIndexService = hmacIndexService;
        this.idempotencyScript = idempotencyScript;
    }

    /**
     * Valida o adquiere la reserva de idempotencia para una transacción.
     * Si la transacción ya existe con diferente payload, lanza IdempotencyConflictException (HTTP 409).
     * Si ya existe y fue completada con el mismo payload, retorna el resultado previo en caché.
     * Si es nueva, la reserva en estado IN_PROGRESS.
     */
    public IdempotencyResult processIdempotency(
            UUID merchantId,
            String transactionId,
            String canonicalPayload,
            long ttlSeconds) {

        if (merchantId == null) {
            throw new IllegalArgumentException("merchantId no puede ser nulo");
        }
        if (transactionId == null || transactionId.trim().isEmpty()) {
            throw new IllegalArgumentException("transactionId no puede ser nulo ni vacío");
        }

        String payloadHash = hmacIndexService.computeSha256(canonicalPayload != null ? canonicalPayload : "");
        String redisKey = buildIdempotencyKey(merchantId, transactionId);

        try {
            String scriptResult = redisTemplate.execute(
                    idempotencyScript,
                    Collections.singletonList(redisKey),
                    payloadHash,
                    String.valueOf(ttlSeconds > 0 ? ttlSeconds : DEFAULT_IDEMPOTENCY_TTL_SECONDS)
            );

            if (scriptResult == null) {
                return IdempotencyResult.acquired(transactionId);
            }

            if ("CONFLICT".equalsIgnoreCase(scriptResult)) {
                log.warn("Conflicto de idempotencia detectado para merchant {} y transactionId {}. Payload alterado.", merchantId, transactionId);
                throw new IdempotencyConflictException(
                        transactionId,
                        "El transactionId '" + transactionId + "' ya fue utilizado con una carga útil diferente.",
                        "Esta transacción ya fue procesada previamente con datos distintos. Verifique el identificador de transacción."
                );
            }

            if (scriptResult.startsWith("COMPLETED:")) {
                String cachedResponse = scriptResult.substring("COMPLETED:".length());
                log.info("Hit de caché de idempotencia para transactionId {}", transactionId);
                return IdempotencyResult.completed(transactionId, cachedResponse);
            }

            if ("IN_PROGRESS".equalsIgnoreCase(scriptResult)) {
                log.info("Transacción en progreso concurrente para transactionId {}", transactionId);
                return IdempotencyResult.inProgress(transactionId);
            }

            return IdempotencyResult.acquired(transactionId);
        } catch (IdempotencyConflictException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Fallo de comunicación con Redis al procesar idempotencia para clave {}", redisKey, ex);
            throw new RedisUnavailableException("idempotency_check", "Fallo al consultar idempotencia en Redis: " + ex.getMessage(), ex);
        }
    }

    /**
     * Sobrecarga de conveniencia con TTL estándar de 24 horas.
     */
    public IdempotencyResult processIdempotency(UUID merchantId, String transactionId, String canonicalPayload) {
        return processIdempotency(merchantId, transactionId, canonicalPayload, DEFAULT_IDEMPOTENCY_TTL_SECONDS);
    }

    /**
     * Marca la transacción como COMPLETED y almacena la respuesta calculada para repeticiones futuras.
     */
    public void markCompleted(UUID merchantId, String transactionId, String responseJson, long ttlSeconds) {
        if (merchantId == null || transactionId == null) {
            return;
        }
        String redisKey = buildIdempotencyKey(merchantId, transactionId);
        long effectiveTtl = ttlSeconds > 0 ? ttlSeconds : DEFAULT_IDEMPOTENCY_TTL_SECONDS;

        try {
            redisTemplate.opsForHash().putAll(redisKey, Map.of(
                    "status", "COMPLETED",
                    "responseJson", responseJson != null ? responseJson : ""
            ));
            redisTemplate.expire(redisKey, Duration.ofSeconds(effectiveTtl));
            log.debug("Idempotencia guardada como COMPLETED para clave {}", redisKey);
        } catch (Exception ex) {
            log.error("Error al persistir respuesta de idempotencia en Redis para clave {}", redisKey, ex);
            throw new RedisUnavailableException("idempotency_save", "Fallo al guardar respuesta de idempotencia en Redis: " + ex.getMessage(), ex);
        }
    }

    public void markCompleted(UUID merchantId, String transactionId, String responseJson) {
        markCompleted(merchantId, transactionId, responseJson, DEFAULT_IDEMPOTENCY_TTL_SECONDS);
    }

    public String buildIdempotencyKey(UUID merchantId, String transactionId) {
        return String.format(IDEMP_KEY_PREFIX, merchantId, transactionId);
    }
}
