package com.pillapago.auth.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servicio de mitigación de fuerza bruta y rate limiting de inicio de sesión (RNF-17).
 * Implementado con Bucket4j: 5 intentos fallidos consecutivos bloquean el acceso durante 15 minutos.
 */
@Service
public class LoginRateLimiterService {

    public static final int MAX_FAILED_ATTEMPTS = 5;
    public static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);

    // Map concurrente en memoria: clave -> Bucket
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    // Map para almacenar fecha límite de bloqueo si se agotó el bucket
    private final Map<String, Long> lockoutTimestamps = new ConcurrentHashMap<>();

    /**
     * Verifica si una clave (IP o correo) se encuentra actualmente bloqueada.
     */
    public boolean isBlocked(String key) {
        if (key == null) return false;

        Long lockoutUntil = lockoutTimestamps.get(key);
        if (lockoutUntil != null) {
            if (System.currentTimeMillis() < lockoutUntil) {
                return true;
            } else {
                // El bloqueo expiró, limpiar
                lockoutTimestamps.remove(key);
                buckets.remove(key);
            }
        }
        return false;
    }

    /**
     * Registra un intento fallido de autenticación.
     * Si se alcanza el límite de 5 intentos, bloquea por 15 minutos.
     */
    public void recordFailedAttempt(String key) {
        if (key == null) return;

        Bucket bucket = buckets.computeIfAbsent(key, k -> createNewBucket());
        boolean consumed = bucket.tryConsume(1);

        if (!consumed || bucket.getAvailableTokens() == 0) {
            // Se agotaron los 5 intentos permitidos: se establece el bloqueo estricto de 15 minutos
            lockoutTimestamps.put(key, System.currentTimeMillis() + LOCKOUT_DURATION.toMillis());
        }
    }

    /**
     * Reinicia el conteo de fallos tras un inicio de sesión exitoso.
     */
    public void recordSuccessfulLogin(String key) {
        if (key == null) return;
        buckets.remove(key);
        lockoutTimestamps.remove(key);
    }

    /**
     * Retorna los segundos restantes de bloqueo para la clave.
     */
    public long getRemainingLockoutSeconds(String key) {
        Long lockoutUntil = lockoutTimestamps.get(key);
        if (lockoutUntil == null) return 0;
        long remainingMs = lockoutUntil - System.currentTimeMillis();
        return Math.max(0, remainingMs / 1000);
    }

    private Bucket createNewBucket() {
        // Capacidad 5, recarga 5 fichas completas cada 15 minutos
        Bandwidth limit = Bandwidth.builder()
                .capacity(MAX_FAILED_ATTEMPTS)
                .refillIntervally(MAX_FAILED_ATTEMPTS, LOCKOUT_DURATION)
                .build();

        return Bucket.builder()
                .addLimit(limit)
                .build();
    }
}
