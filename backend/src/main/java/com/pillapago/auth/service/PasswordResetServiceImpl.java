package com.pillapago.auth.service;

import com.pillapago.auth.domain.PasswordResetToken;
import com.pillapago.auth.dto.ForgotPasswordRequest;
import com.pillapago.auth.dto.MessageResponse;
import com.pillapago.auth.dto.ResetPasswordRequest;
import com.pillapago.auth.repository.PasswordResetTokenRepository;
import com.pillapago.auth.repository.RefreshTokenRepository;
import com.pillapago.shared.correo.CorreoPort;
import com.pillapago.shared.exception.InvalidVerificationCodeException;
import com.pillapago.shared.exception.PasswordMismatchException;
import com.pillapago.shared.exception.RateLimitExceededException;
import com.pillapago.shared.security.JwtService;
import com.pillapago.tenant.domain.User;
import com.pillapago.tenant.domain.UserStatus;
import com.pillapago.tenant.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetServiceImpl implements PasswordResetService {

    public static final String STANDARD_REQUEST_RESPONSE = "Si el correo existe, enviaremos instrucciones";
    private static final int RESET_TOKEN_TTL_MINUTES = 30;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CorreoPort correoPort;
    private final PasswordResetRateLimiterService rateLimiterService;

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional
    public MessageResponse requestPasswordReset(ForgotPasswordRequest request, String clientIp) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        String ipKey = "ip:" + (clientIp != null ? clientIp : "127.0.0.1");
        String emailKey = "email:" + normalizedEmail;

        // 1. Control de abuso y Rate Limiting (Bucket4j - RNF-17)
        boolean ipAllowed = rateLimiterService.tryConsume(ipKey);
        boolean emailAllowed = rateLimiterService.tryConsume(emailKey);

        if (!ipAllowed || !emailAllowed) {
            long retry = Math.max(
                    rateLimiterService.getRemainingLockoutSeconds(ipKey),
                    rateLimiterService.getRemainingLockoutSeconds(emailKey)
            );
            log.warn("Solicitud de recuperación de contraseña bloqueada por rate limit. IP: {}, Email: {}", clientIp, normalizedEmail);
            throw new RateLimitExceededException(
                    "Demasiadas solicitudes de recuperación de contraseña. Intente nuevamente en 15 minutos.",
                    retry > 0 ? retry : 900
            );
        }

        // 2. Búsqueda de usuario (por email plano o por emailHash)
        String emailHash = jwtService.hashToken(normalizedEmail);
        Optional<User> userOpt = userRepository.findByEmailIgnoreCase(normalizedEmail);
        if (userOpt.isEmpty()) {
            userOpt = userRepository.findByEmailHash(emailHash);
        }

        // 3. Mitigación estricta de enumeración de usuarios:
        // Si no existe o no está activo, retornamos de inmediato la respuesta estándar 200 OK
        if (userOpt.isEmpty()) {
            log.info("Solicitud de recuperación para correo no registrado: {}", normalizedEmail);
            return new MessageResponse(STANDARD_REQUEST_RESPONSE);
        }

        User user = userOpt.get();
        if (user.getStatus() != UserStatus.ACTIVE) {
            log.info("Solicitud de recuperación para usuario con estado {}: {}", user.getStatus(), normalizedEmail);
            return new MessageResponse(STANDARD_REQUEST_RESPONSE);
        }

        // 4. Invalidar tokens de recuperación previos pendientes del usuario
        passwordResetTokenRepository.invalidatePendingTokens(user.getId());

        // 5. Generar token criptográficamente seguro (32 bytes aleatorios en Hexadecimal)
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String rawToken = HexFormat.of().formatHex(randomBytes);
        String tokenHash = jwtService.hashToken(rawToken);

        // 6. Almacenar el hash del token con TTL de 30 minutos y un solo uso
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(OffsetDateTime.now().plusMinutes(RESET_TOKEN_TTL_MINUTES))
                .used(false)
                .build();
        passwordResetTokenRepository.save(resetToken);

        // 7. Enviar instrucciones por correo electrónico
        correoPort.enviarRecuperacionContrasena(normalizedEmail, user.getUsername(), rawToken);
        log.info("Token de recuperación de contraseña generado exitosamente para usuario: {}", normalizedEmail);

        // 8. Respuesta estándar 200 OK
        return new MessageResponse(STANDARD_REQUEST_RESPONSE);
    }

    @Override
    @Transactional
    public MessageResponse resetPassword(ResetPasswordRequest request) {
        // 1. Validar coincidencia de contraseñas
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new PasswordMismatchException("Las contraseñas no coinciden");
        }

        // 2. Búsqueda del token por su hash SHA-256
        String tokenHash = jwtService.hashToken(request.getToken().trim());
        PasswordResetToken tokenEntity = passwordResetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new InvalidVerificationCodeException("El enlace de recuperación es inválido o no existe."));

        // 3. Validar si ya fue utilizado (enlace de un solo uso)
        if (Boolean.TRUE.equals(tokenEntity.getUsed())) {
            log.warn("Intento de reutilización de token de recuperación para usuario ID: {}", tokenEntity.getUser().getId());
            throw new InvalidVerificationCodeException("Este enlace de recuperación ya ha sido utilizado. Solicita uno nuevo.");
        }

        // 4. Validar expiración (vence en 30 minutos)
        if (tokenEntity.isExpired()) {
            log.warn("Intento de uso de token de recuperación expirado para usuario ID: {}", tokenEntity.getUser().getId());
            throw new InvalidVerificationCodeException("El enlace de recuperación ha expirado. Por favor solicita uno nuevo.");
        }

        // 5. Marcar token como consumido (un solo uso)
        tokenEntity.setUsed(true);
        passwordResetTokenRepository.save(tokenEntity);

        // 6. Actualizar el hash de la contraseña del usuario (BCrypt con costo >= 12)
        User user = tokenEntity.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        userRepository.save(user);

        // 7. Revocar TODOS los refresh tokens activos del usuario (cierre forzado de sesiones previas)
        int revokedCount = refreshTokenRepository.revokeAllByUserId(user.getId());
        log.info("Contraseña actualizada exitosamente para usuario {}. Se revocaron {} refresh tokens activos.",
                user.getEmail(), revokedCount);

        return new MessageResponse("Contraseña restablecida exitosamente. Redirigiendo al inicio de sesión.");
    }
}
