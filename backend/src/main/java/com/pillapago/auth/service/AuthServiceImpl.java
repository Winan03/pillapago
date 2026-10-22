package com.pillapago.auth.service;

import com.pillapago.auth.domain.RefreshToken;
import com.pillapago.auth.dto.*;
import com.pillapago.auth.repository.RefreshTokenRepository;
import com.pillapago.shared.exception.InvalidCredentialsException;
import com.pillapago.shared.exception.RateLimitExceededException;
import com.pillapago.shared.security.JwtService;
import com.pillapago.shared.security.UserPrincipal;
import com.pillapago.tenant.domain.User;
import com.pillapago.tenant.domain.UserStatus;
import com.pillapago.tenant.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    // Hash dummy precalculado con BCrypt costo 12 para mitigar ataques de temporización (timing attacks)
    private static final String DUMMY_BCRYPT_HASH = "$2a$12$e8UvVvL4N4kL1fW1I61mIuY0mXw1aF7qF4l1sY3QY61w1aF7qF4l1";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginRateLimiterService rateLimiterService;

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request, String clientIp) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        String ipKey = "ip:" + (clientIp != null ? clientIp : "127.0.0.1");
        String emailKey = "email:" + normalizedEmail;

        // 1. Control de abuso (Rate Limiting con Bucket4j - RNF-17)
        if (rateLimiterService.isBlocked(ipKey) || rateLimiterService.isBlocked(emailKey)) {
            long retry = Math.max(
                    rateLimiterService.getRemainingLockoutSeconds(ipKey),
                    rateLimiterService.getRemainingLockoutSeconds(emailKey)
            );
            log.warn("Intento de login bloqueado por exceso de intentos fallidos. IP: {}, Email: {}", clientIp, normalizedEmail);
            throw new RateLimitExceededException(
                    "Demasiados intentos fallidos. Su acceso ha sido bloqueado temporalmente por 15 minutos.",
                    retry > 0 ? retry : 900
            );
        }

        // 2. Búsqueda de usuario (por email o emailHash)
        String emailHash = jwtService.hashToken(normalizedEmail);
        Optional<User> userOpt = userRepository.findByEmailIgnoreCase(normalizedEmail);
        if (userOpt.isEmpty()) {
            userOpt = userRepository.findByEmailHash(emailHash);
        }

        // 3. Mitigación de enumeración de usuarios: Si no existe, ejecutar dummy match
        if (userOpt.isEmpty()) {
            passwordEncoder.matches(request.getPassword(), DUMMY_BCRYPT_HASH);
            recordFailedAttempt(ipKey, emailKey);
            throw new InvalidCredentialsException("El correo electrónico o la contraseña ingresados no coinciden. Por favor verifica tus datos e intenta nuevamente.");
        }

        User user = userOpt.get();

        // 4. Verificación de contraseña
        boolean passwordMatches = passwordEncoder.matches(request.getPassword(), user.getPasswordHash());
        if (!passwordMatches) {
            recordFailedAttempt(ipKey, emailKey);
            throw new InvalidCredentialsException("El correo electrónico o la contraseña ingresados no coinciden. Por favor verifica tus datos e intenta nuevamente.");
        }

        // 5. Estado de la cuenta: Explicación amigable y literal al usuario sin filtrar datos
        if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            throw new InvalidCredentialsException("Tu cuenta aún no está activa. Revisa tu correo e ingresa el código de 6 dígitos para activarla antes de iniciar sesión.");
        }

        if (user.getStatus() == UserStatus.DISABLED) {
            throw new InvalidCredentialsException("Tu cuenta se encuentra inhabilitada. Por favor contacta al soporte de Pillapago.");
        }

        // 6. Credenciales válidas: reiniciar contador de fallos
        rateLimiterService.recordSuccessfulLogin(ipKey);
        rateLimiterService.recordSuccessfulLogin(emailKey);

        // 7. Generar Access Token (15 minutos) y Refresh Token rotativo
        UserPrincipal principal = UserPrincipal.fromUser(user);
        String accessToken = jwtService.generateAccessToken(principal);
        String refreshTokenValue = jwtService.generateRefreshTokenValue();
        String refreshTokenHash = jwtService.hashToken(refreshTokenValue);

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(refreshTokenHash)
                .expiresAt(OffsetDateTime.now().plusDays(7))
                .revoked(false)
                .build();
        refreshTokenRepository.save(refreshToken);

        UserDto userDto = UserDto.builder()
                .id(user.getId())
                .merchantId(user.getMerchant() != null ? user.getMerchant().getId() : null)
                .email(user.getEmail() != null ? user.getEmail() : normalizedEmail)
                .username(user.getUsername())
                .role(user.getRole())
                .build();

        log.info("Inicio de sesión exitoso para usuario: {} [Rol: {}]", user.getUsername(), user.getRole());

        return LoginResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpirationSeconds()) // 900s = 15 min
                .refreshToken(refreshTokenValue)
                .user(userDto)
                .build();
    }

    @Override
    @Transactional
    public MessageResponse logout(LogoutRequest request, UserPrincipal currentUser) {
        if (request != null && request.getRefreshToken() != null) {
            String tokenHash = jwtService.hashToken(request.getRefreshToken().trim());
            Optional<RefreshToken> rtOpt = refreshTokenRepository.findByTokenHash(tokenHash);
            if (rtOpt.isPresent()) {
                RefreshToken rt = rtOpt.get();
                rt.setRevoked(true);
                refreshTokenRepository.save(rt);
                log.info("Refresh token invalidado correctamente en logout");
            }
        }

        SecurityContextHolder.clearContext();
        return new MessageResponse("Sesión cerrada exitosamente");
    }

    private void recordFailedAttempt(String ipKey, String emailKey) {
        rateLimiterService.recordFailedAttempt(ipKey);
        rateLimiterService.recordFailedAttempt(emailKey);
    }
}
