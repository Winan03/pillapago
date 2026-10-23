package com.pillapago.auth.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servicio de mitigación de abuso y rate limiting para solicitudes de recuperación de contraseña (HU-03, RNF-17).
 * Implementado con Bucket4j: máximo 3 solicitudes cada 15 minutos por IP o por correo electrónico.
 */
@Service
public class PasswordResetRateLimiterService {

    public static final int MAX_REQUESTS = 3;
    public static final Duration WINDOW_DURATION = Duration.ofMinutes(15);

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final Map<String, Long> lockoutTimestamps = new ConcurrentHashMap<>();

    /**
     * Intenta consumir un token de la clave (ip:xxx o email:xxx).
     * Si no hay tokens disponibles, bloquea la clave durante el resto de la ventana y retorna false.
     */
    public boolean tryConsume(String key) {
        if (key == null) return true;

        Long lockoutUntil = lockoutTimestamps.get(key);
        if (lockoutUntil != null) {
            if (System.currentTimeMillis() < lockoutUntil) {
                return false;
            } else {
                lockoutTimestamps.remove(key);
                buckets.remove(key);
            }
        }

        Bucket bucket = buckets.computeIfAbsent(key, k -> createNewBucket());
        boolean consumed = bucket.tryConsume(1);

        if (!consumed || bucket.getAvailableTokens() == 0) {
            lockoutTimestamps.put(key, System.currentTimeMillis() + WINDOW_DURATION.toMillis());
        }

        return consumed;
    }

    /**
     * Retorna los segundos restantes para reintentar.
     */
    public long getRemainingLockoutSeconds(String key) {
        Long lockoutUntil = lockoutTimestamps.get(key);
        if (lockoutUntil == null) return 0;
        long remainingMs = lockoutUntil - System.currentTimeMillis();
        return Math.max(0, remainingMs / 1000);
    }

    /**
     * Limpia el estado de rate limiting (útil para tests).
     */
    public void reset() {
        buckets.clear();
        lockoutTimestamps.clear();
    }

    private Bucket createNewBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(MAX_REQUESTS)
                .refillIntervally(MAX_REQUESTS, WINDOW_DURATION)
                .build();

        return Bucket.builder()
                .addLimit(limit)
                .build();
    }
}
