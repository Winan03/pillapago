package com.pillapago.evaluation.service;

import com.pillapago.shared.crypto.HmacIndexService;
import com.pillapago.shared.exception.RedisUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Servicio de métricas de velocidad (Velocity) mediante ventanas deslizantes en Redis.
 *
 * Cumple estrictamente con:
 * - Aislamiento Multi-Tenant: claves con prefijo "t:{merchantId}:...".
 * - RNF-32: Seudonimización con HMAC-SHA256 para todos los identificadores antes de tocar Redis.
 * - Atomicidad: Ejecución exclusiva mediante scripts compilados de Lua (evitando condiciones de carrera).
 * - Tolerancia a fallos: Captura fallos de infraestructura y lanza RedisUnavailableException.
 */
@Service
public class RedisVelocityService {

    private static final Logger log = LoggerFactory.getLogger(RedisVelocityService.class);

    private static final String VELOCITY_FREQ_PREFIX = "t:%s:vel:freq:%s:%s";
    private static final String VELOCITY_DIST_PREFIX = "t:%s:vel:dist:%s:%s";

    private final StringRedisTemplate redisTemplate;
    private final HmacIndexService hmacIndexService;
    private final RedisScript<Long> slidingWindowFrequencyScript;
    private final RedisScript<Long> slidingWindowDistinctScript;

    public RedisVelocityService(
            StringRedisTemplate redisTemplate,
            HmacIndexService hmacIndexService,
            RedisScript<Long> slidingWindowFrequencyScript,
            RedisScript<Long> slidingWindowDistinctScript) {
        this.redisTemplate = redisTemplate;
        this.hmacIndexService = hmacIndexService;
        this.slidingWindowFrequencyScript = slidingWindowFrequencyScript;
        this.slidingWindowDistinctScript = slidingWindowDistinctScript;
    }

    /**
     * Registra un evento y obtiene la frecuencia acumulada en una ventana deslizante.
     *
     * @param merchantId Identificador del comercio (Tenant)
     * @param metric Nombre de la métrica (ej: "device_txns", "ip_txns")
     * @param rawIdentifier Identificador sensible en claro (ej: deviceId, ipAddress)
     * @param windowSeconds Duración de la ventana en segundos (ej: 600 para 10 min)
     * @param eventId Identificador único del evento para el ZSET member (ej: transactionId)
     * @param nowMs Timestamp actual en epoch millis
     * @return Conteo exacto de eventos vigentes en la ventana deslizante
     */
    public long recordAndGetFrequency(
            UUID merchantId,
            String metric,
            String rawIdentifier,
            long windowSeconds,
            String eventId,
            long nowMs) {

        if (merchantId == null) {
            throw new IllegalArgumentException("merchantId no puede ser nulo");
        }

        // RNF-32: Seudonimización con HMAC antes de formar la clave en Redis
        String hmacIdentifier = hmacIndexService.computeBlindIndex(merchantId, rawIdentifier);
        String redisKey = buildFrequencyKey(merchantId, metric, hmacIdentifier);

        long windowMs = windowSeconds * 1000L;
        long ttlSeconds = windowSeconds + 60L; // Margen de gracia para evitar acumulación
        String member = eventId != null ? (eventId + ":" + nowMs) : UUID.randomUUID() + ":" + nowMs;

        try {
            Long count = redisTemplate.execute(
                    slidingWindowFrequencyScript,
                    Collections.singletonList(redisKey),
                    String.valueOf(nowMs),
                    String.valueOf(windowMs),
                    member,
                    String.valueOf(ttlSeconds)
            );
            return count != null ? count : 0L;
        } catch (Exception ex) {
            log.error("Fallo de comunicación con Redis al calcular velocity de frecuencia para clave {}", redisKey, ex);
            throw new RedisUnavailableException("velocity_frequency", "Fallo al consultar velocity en Redis: " + ex.getMessage(), ex);
        }
    }

    /**
     * Sobrecarga de conveniencia que utiliza el timestamp del sistema actual.
     */
    public long recordAndGetFrequency(
            UUID merchantId,
            String metric,
            String rawIdentifier,
            long windowSeconds,
            String eventId) {
        return recordAndGetFrequency(merchantId, metric, rawIdentifier, windowSeconds, eventId, System.currentTimeMillis());
    }

    /**
     * Registra y calcula el conteo de entidades distintas asociadas a un identificador principal
     * dentro de una ventana deslizante (ej: cuentas distintas asociadas a un mismo dispositivo).
     *
     * @param merchantId Identificador del comercio (Tenant)
     * @param metric Nombre de la métrica (ej: "device_accounts")
     * @param rawIdentifier Identificador principal en claro (ej: deviceId)
     * @param rawTargetEntity Identificador secundario en claro a contar distintamente (ej: accountId, customerEmail)
     * @param windowSeconds Duración de la ventana en segundos
     * @param nowMs Timestamp actual en epoch millis
     * @return Cantidad de entidades distintas activas en la ventana
     */
    public long recordAndGetDistinctCount(
            UUID merchantId,
            String metric,
            String rawIdentifier,
            String rawTargetEntity,
            long windowSeconds,
            long nowMs) {

        if (merchantId == null) {
            throw new IllegalArgumentException("merchantId no puede ser nulo");
        }

        // RNF-32: Seudonimización con HMAC de AMBOS identificadores sensibles
        String hmacIdentifier = hmacIndexService.computeBlindIndex(merchantId, rawIdentifier);
        String hmacTargetEntity = hmacIndexService.computeBlindIndex(merchantId, rawTargetEntity);

        String redisKey = buildDistinctKey(merchantId, metric, hmacIdentifier);
        long windowMs = windowSeconds * 1000L;
        long ttlSeconds = windowSeconds + 60L;

        try {
            Long distinctCount = redisTemplate.execute(
                    slidingWindowDistinctScript,
                    Collections.singletonList(redisKey),
                    String.valueOf(nowMs),
                    String.valueOf(windowMs),
                    hmacTargetEntity,
                    String.valueOf(ttlSeconds)
            );
            return distinctCount != null ? distinctCount : 0L;
        } catch (Exception ex) {
            log.error("Fallo de comunicación con Redis al calcular velocity de entidades distintas para clave {}", redisKey, ex);
            throw new RedisUnavailableException("velocity_distinct", "Fallo al consultar velocity distinta en Redis: " + ex.getMessage(), ex);
        }
    }

    /**
     * Sobrecarga de conveniencia con timestamp del sistema.
     */
    public long recordAndGetDistinctCount(
            UUID merchantId,
            String metric,
            String rawIdentifier,
            String rawTargetEntity,
            long windowSeconds) {
        return recordAndGetDistinctCount(merchantId, metric, rawIdentifier, rawTargetEntity, windowSeconds, System.currentTimeMillis());
    }

    /**
     * Retorna la clave Redis formateada para frecuencia (útil para auditoría e inspección).
     */
    public String buildFrequencyKey(UUID merchantId, String metric, String hmacIdentifier) {
        return String.format(VELOCITY_FREQ_PREFIX, merchantId, metric, hmacIdentifier);
    }

    /**
     * Retorna la clave Redis formateada para conteo distinto (útil para auditoría e inspección).
     */
    public String buildDistinctKey(UUID merchantId, String metric, String hmacIdentifier) {
        return String.format(VELOCITY_DIST_PREFIX, merchantId, metric, hmacIdentifier);
    }
}
