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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private CorreoPort correoPort;
    @Mock private PasswordResetRateLimiterService rateLimiterService;

    @InjectMocks
    private PasswordResetServiceImpl passwordResetService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .id(UUID.randomUUID())
                .username("juanadmin")
                .email("juan@comercio.pe")
                .emailHash("hash_juan")
                .passwordHash("old_hashed_password")
                .status(UserStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("Solicitud de recuperación: usuario no existe responde 200 con mensaje estándar sin divulgar")
    void requestPasswordReset_UserNotFound_ReturnsStandardMessageWithoutDisclosing() {
        ForgotPasswordRequest req = new ForgotPasswordRequest("noexiste@comercio.pe");
        when(rateLimiterService.tryConsume(anyString())).thenReturn(true);
        when(jwtService.hashToken(anyString())).thenReturn("dummy_hash");
        when(userRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(userRepository.findByEmailHash(anyString())).thenReturn(Optional.empty());

        MessageResponse response = passwordResetService.requestPasswordReset(req, "192.168.1.1");

        assertThat(response.getMessage()).isEqualTo(PasswordResetServiceImpl.STANDARD_REQUEST_RESPONSE);
        verify(correoPort, never()).enviarRecuperacionContrasena(any(), any(), any());
        verify(passwordResetTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("Solicitud de recuperación: usuario no activo responde 200 con mensaje estándar sin enviar correo")
    void requestPasswordReset_UserNotActive_ReturnsStandardMessageWithoutSendingEmail() {
        sampleUser.setStatus(UserStatus.PENDING_VERIFICATION);
        ForgotPasswordRequest req = new ForgotPasswordRequest(sampleUser.getEmail());
        when(rateLimiterService.tryConsume(anyString())).thenReturn(true);
        when(jwtService.hashToken(anyString())).thenReturn("dummy_hash");
        when(userRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.of(sampleUser));

        MessageResponse response = passwordResetService.requestPasswordReset(req, "192.168.1.1");

        assertThat(response.getMessage()).isEqualTo(PasswordResetServiceImpl.STANDARD_REQUEST_RESPONSE);
        verify(correoPort, never()).enviarRecuperacionContrasena(any(), any(), any());
    }

    @Test
    @DisplayName("Solicitud de recuperación: usuario activo genera token de 30m y envía correo")
    void requestPasswordReset_UserActive_GeneratesTokenAndSendsEmail() {
        ForgotPasswordRequest req = new ForgotPasswordRequest(sampleUser.getEmail());
        when(rateLimiterService.tryConsume(anyString())).thenReturn(true);
        when(jwtService.hashToken(anyString())).thenReturn("token_sha256_hash");
        when(userRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.of(sampleUser));

        MessageResponse response = passwordResetService.requestPasswordReset(req, "192.168.1.1");

        assertThat(response.getMessage()).isEqualTo(PasswordResetServiceImpl.STANDARD_REQUEST_RESPONSE);
        verify(passwordResetTokenRepository).invalidatePendingTokens(sampleUser.getId());

        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository).save(tokenCaptor.capture());
        PasswordResetToken savedToken = tokenCaptor.getValue();
        assertThat(savedToken.getUser()).isEqualTo(sampleUser);
        assertThat(savedToken.getUsed()).isFalse();

        verify(correoPort).enviarRecuperacionContrasena(eq(sampleUser.getEmail()), eq(sampleUser.getUsername()), anyString());
    }

    @Test
    @DisplayName("Solicitud de recuperación: rate limiting excedido lanza RateLimitExceededException")
    void requestPasswordReset_RateLimitExceeded_ThrowsRateLimitExceededException() {
        ForgotPasswordRequest req = new ForgotPasswordRequest(sampleUser.getEmail());
        when(rateLimiterService.tryConsume("ip:192.168.1.1")).thenReturn(false);
        when(rateLimiterService.getRemainingLockoutSeconds(anyString())).thenReturn(900L);

        assertThatThrownBy(() -> passwordResetService.requestPasswordReset(req, "192.168.1.1"))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("Demasiadas solicitudes de recuperación");

        verifyNoInteractions(userRepository);
        verifyNoInteractions(correoPort);
    }

    @Test
    @DisplayName("Restablecimiento: contraseñas no coinciden lanza PasswordMismatchException")
    void resetPassword_PasswordMismatch_ThrowsPasswordMismatchException() {
        ResetPasswordRequest req = ResetPasswordRequest.builder()
                .token("any_token")
                .password("Password123#")
                .confirmPassword("DifferentPassword123#")
                .build();

        assertThatThrownBy(() -> passwordResetService.resetPassword(req))
                .isInstanceOf(PasswordMismatchException.class)
                .hasMessageContaining("Las contraseñas no coinciden");

        verifyNoInteractions(passwordResetTokenRepository);
    }

    @Test
    @DisplayName("Restablecimiento: token no encontrado lanza InvalidVerificationCodeException")
    void resetPassword_TokenNotFound_ThrowsInvalidVerificationCodeException() {
        ResetPasswordRequest req = ResetPasswordRequest.builder()
                .token("invalid_token")
                .password("NewPassword123#")
                .confirmPassword("NewPassword123#")
                .build();

        when(jwtService.hashToken("invalid_token")).thenReturn("hash_inv");
        when(passwordResetTokenRepository.findByTokenHash("hash_inv")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> passwordResetService.resetPassword(req))
                .isInstanceOf(InvalidVerificationCodeException.class)
                .hasMessageContaining("inválido o no existe");
    }

    @Test
    @DisplayName("Restablecimiento: token ya usado lanza InvalidVerificationCodeException")
    void resetPassword_TokenAlreadyUsed_ThrowsInvalidVerificationCodeException() {
        ResetPasswordRequest req = ResetPasswordRequest.builder()
                .token("used_token")
                .password("NewPassword123#")
                .confirmPassword("NewPassword123#")
                .build();

        PasswordResetToken token = PasswordResetToken.builder()
                .user(sampleUser)
                .tokenHash("hash_used")
                .expiresAt(OffsetDateTime.now().plusMinutes(20))
                .used(true)
                .build();

        when(jwtService.hashToken("used_token")).thenReturn("hash_used");
        when(passwordResetTokenRepository.findByTokenHash("hash_used")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> passwordResetService.resetPassword(req))
                .isInstanceOf(InvalidVerificationCodeException.class)
                .hasMessageContaining("ya ha sido utilizado");
    }

    @Test
    @DisplayName("Restablecimiento: token expirado lanza InvalidVerificationCodeException")
    void resetPassword_TokenExpired_ThrowsInvalidVerificationCodeException() {
        ResetPasswordRequest req = ResetPasswordRequest.builder()
                .token("expired_token")
                .password("NewPassword123#")
                .confirmPassword("NewPassword123#")
                .build();

        PasswordResetToken token = PasswordResetToken.builder()
                .user(sampleUser)
                .tokenHash("hash_exp")
                .expiresAt(OffsetDateTime.now().minusMinutes(5)) // Expirado hace 5 min
                .used(false)
                .build();

        when(jwtService.hashToken("expired_token")).thenReturn("hash_exp");
        when(passwordResetTokenRepository.findByTokenHash("hash_exp")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> passwordResetService.resetPassword(req))
                .isInstanceOf(InvalidVerificationCodeException.class)
                .hasMessageContaining("ha expirado");
    }

    @Test
    @DisplayName("Restablecimiento exitoso: actualiza hash, revoca refresh tokens y marca token usado")
    void resetPassword_Success_UpdatesHashRevokesTokensAndMarksUsed() {
        ResetPasswordRequest req = ResetPasswordRequest.builder()
                .token("valid_token")
                .password("NewPassword123#")
                .confirmPassword("NewPassword123#")
                .build();

        PasswordResetToken token = PasswordResetToken.builder()
                .user(sampleUser)
                .tokenHash("hash_valid")
                .expiresAt(OffsetDateTime.now().plusMinutes(25))
                .used(false)
                .build();

        when(jwtService.hashToken("valid_token")).thenReturn("hash_valid");
        when(passwordResetTokenRepository.findByTokenHash("hash_valid")).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("NewPassword123#")).thenReturn("$2a$12$newBcryptHashedPassword");
        when(refreshTokenRepository.revokeAllByUserId(sampleUser.getId())).thenReturn(3);

        MessageResponse response = passwordResetService.resetPassword(req);

        assertThat(response.getMessage()).contains("exitosa");
        assertThat(token.getUsed()).isTrue();
        verify(passwordResetTokenRepository).save(token);
        verify(userRepository).save(sampleUser);
        assertThat(sampleUser.getPasswordHash()).isEqualTo("$2a$12$newBcryptHashedPassword");
        verify(refreshTokenRepository).revokeAllByUserId(sampleUser.getId());
    }
}
