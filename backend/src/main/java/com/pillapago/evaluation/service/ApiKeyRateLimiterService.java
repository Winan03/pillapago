package com.pillapago.evaluation.service;

import com.pillapago.shared.exception.RateLimitExceededException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Control de tasa de peticiones (Rate Limiting) por API Key utilizando Bucket4j (HU-06c).
 * Garantiza que cada comercio o clave de API no exceda la cuota de peticiones por segundo.
 */
@Service
public class ApiKeyRateLimiterService {

    private final Map<UUID, Bucket> apiKeyBuckets = new ConcurrentHashMap<>();
    private volatile int capacity = 100;
    private volatile Duration refillDuration = Duration.ofSeconds(1);

    /**
     * Valida el consumo de un token para la clave de API indicada.
     * Si no hay tokens disponibles, lanza RateLimitExceededException (HTTP 429).
     */
    public void checkRateLimit(UUID apiKeyId) {
        if (apiKeyId == null) {
            return;
        }

        Bucket bucket = apiKeyBuckets.computeIfAbsent(apiKeyId, k -> createBucket());
        if (!bucket.tryConsume(1)) {
            throw new RateLimitExceededException(
                    "Ha superado el límite de peticiones permitido para su API Key. Intente más tarde.",
                    1L
            );
        }
    }

    /**
     * Configuración dinámica de capacidad para pruebas o ajustes por tier.
     */
    public void configureLimit(int capacity, Duration refillDuration) {
        this.capacity = capacity;
        this.refillDuration = refillDuration;
        this.apiKeyBuckets.clear();
    }

    /**
     * Limpia los buckets acumulados.
     */
    public void reset() {
        this.apiKeyBuckets.clear();
    }

    private Bucket createBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(capacity, refillDuration)
                .build();
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }
}
