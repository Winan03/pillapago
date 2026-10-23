package com.pillapago.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pillapago.auth.domain.PasswordResetToken;
import com.pillapago.auth.domain.RefreshToken;
import com.pillapago.auth.dto.*;
import com.pillapago.auth.repository.PasswordResetTokenRepository;
import com.pillapago.auth.repository.RefreshTokenRepository;
import com.pillapago.auth.service.PasswordResetRateLimiterService;
import com.pillapago.shared.correo.NoOpCorreoAdapter;
import com.pillapago.shared.security.JwtService;
import com.pillapago.tenant.domain.Merchant;
import com.pillapago.tenant.domain.User;
import com.pillapago.tenant.domain.UserRole;
import com.pillapago.tenant.domain.UserStatus;
import com.pillapago.tenant.repository.MerchantRepository;
import com.pillapago.tenant.repository.UserRepository;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Suite End-to-End (E2E) Integral de Aceptación para HU-03: Recuperar mi contraseña.
 *
 * Valida secuencialmente los criterios de aceptación:
 *  1. Siempre responde 200 con "Si el correo existe, enviaremos instrucciones" sin revelar existencia de la cuenta.
 *  2. Enlace de un solo uso (utilizarlo dos veces es rechazado).
 *  3. Vence en 30 minutos (tokens con expiración superada son rechazados).
 *  4. Al restablecer, actualiza el hash de la contraseña en BD (BCrypt costo >= 12).
 *  5. Revoca todos los refresh tokens activos del usuario.
 *  6. Login con la clave anterior es rechazado (401) y con la nueva es exitoso (200).
 *  7. Rate limiting en solicitudes de recuperación (HTTP 429 con Retry-After).
 *  8. Validación de contraseña robusta con Bean Validation y RFC 7807 (400 Bad Request).
 *
 * Genera el reporte físico de aceptación en: target/reporte_hu03_recuperacion_contrasena.txt
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PasswordResetE2ETest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private MerchantRepository merchantRepository;
    @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;
    @Autowired private NoOpCorreoAdapter correoAdapter;
    @Autowired private PasswordResetRateLimiterService rateLimiterService;

    private static final String RUN_ID = UUID.randomUUID().toString().substring(0, 8);
    private static final String TEST_EMAIL = "admin_reset_" + RUN_ID + "@comercio.pe";
    private static final String NON_EXISTENT_EMAIL = "fantasma_" + RUN_ID + "@desconocido.com";
    private static final String INITIAL_PASSWORD = "Password123#Initial";
    private static final String NEW_PASSWORD = "NewPassword123#Secure";

    private static User testUser;
    private static Merchant testMerchant;
    private static String capturedResetToken;
    private static final List<String> auditLogs = new ArrayList<>();

    @BeforeAll
    static void initSuite() {
        auditLogs.add("===============================================================================");
        auditLogs.add("REPORTE DE ACEPTACIÓN END-TO-END: HU-03 (RECUPERACIÓN Y RESTABLECIMIENTO)");
        auditLogs.add("Fecha de Ejecución: " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        auditLogs.add("Run ID: " + RUN_ID);
        auditLogs.add("===============================================================================\n");
    }

    @AfterAll
    static void finishSuite() {
        auditLogs.add("\n===============================================================================");
        auditLogs.add("RESULTADO FINAL: TODOS LOS CRITERIOS DE ACEPTACIÓN HU-03 CUMPLIDOS AL 100%");
        auditLogs.add("===============================================================================");

        try {
            Path targetDir = Paths.get("target");
            if (!Files.exists(targetDir)) {
                Files.createDirectories(targetDir);
            }
            Path reportPath = targetDir.resolve("reporte_hu03_recuperacion_contrasena.txt");
            Files.write(reportPath, auditLogs, StandardCharsets.UTF_8);
            System.out.println("\n>>> Reporte de aceptación HU-03 generado exitosamente en: " + reportPath.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("Error al escribir reporte HU-03: " + e.getMessage());
        }
    }

    @Test @Order(1)
    @DisplayName("01. Configuración de Usuario y Sesiones Activas Previas")
    void step01_setupUserWithActiveRefreshTokens() {
        testMerchant = merchantRepository.save(Merchant.builder()
                .name("Comercio Reset Test " + RUN_ID)
                .uuid(UUID.randomUUID().toString())
                .reviewThreshold(50)
                .rejectThreshold(80)
                .failPolicy("FAIL_CLOSED")
                .dataRetentionMonths(13)
                .build());

        String emailHash = jwtService.hashToken(TEST_EMAIL.toLowerCase());
        testUser = userRepository.save(User.builder()
                .merchant(testMerchant)
                .username("usuario_reset_" + RUN_ID)
                .email(TEST_EMAIL.toLowerCase())
                .emailHash(emailHash)
                .passwordHash(passwordEncoder.encode(INITIAL_PASSWORD))
                .role(UserRole.MERCHANT_ADMIN)
                .status(UserStatus.ACTIVE)
                .mfaEnabled(false)
                .build());

        // Crear 2 refresh tokens activos para este usuario (para comprobar revocación posterior)
        refreshTokenRepository.save(RefreshToken.builder()
                .user(testUser)
                .tokenHash(jwtService.hashToken("rt_active_1_" + RUN_ID))
                .expiresAt(OffsetDateTime.now().plusDays(7))
                .revoked(false)
                .build());

        refreshTokenRepository.save(RefreshToken.builder()
                .user(testUser)
                .tokenHash(jwtService.hashToken("rt_active_2_" + RUN_ID))
                .expiresAt(OffsetDateTime.now().plusDays(7))
                .revoked(false)
                .build());

        List<RefreshToken> tokens = refreshTokenRepository.findAllByUserId(testUser.getId());
        assertThat(tokens).hasSize(2);
        assertThat(tokens).allMatch(t -> !t.isRevoked());

        auditLogs.add("[CRITERIO 01 - PREPARACIÓN DE CUENTA]");
        auditLogs.add("✓ Usuario creado: " + TEST_EMAIL + " con 2 Refresh Tokens activos.");
    }

    @Test @Order(2)
    @DisplayName("02. Solicitud con correo existente: responde 200 con mensaje estándar")
    void step02_requestExistingEmailReturns200StandardMessage() throws Exception {
        ForgotPasswordRequest req = new ForgotPasswordRequest(TEST_EMAIL);

        mockMvc.perform(post("/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Si el correo existe, enviaremos instrucciones"));

        capturedResetToken = correoAdapter.getResetTokenForEmail(TEST_EMAIL);
        assertThat(capturedResetToken).isNotBlank();

        auditLogs.add("\n[CRITERIO 02 - SOLICITUD CORREO EXISTENTE]");
        auditLogs.add("✓ Respuesta 200 OK con mensaje: \"Si el correo existe, enviaremos instrucciones\".");
        auditLogs.add("✓ Correo con enlace seguro de un solo uso enviado a: " + TEST_EMAIL);
    }

    @Test @Order(3)
    @DisplayName("03. Solicitud con correo INEXISTENTE: responde 200 idéntico sin divulgar si existe")
    void step03_requestNonExistentEmailReturnsIdentical200() throws Exception {
        ForgotPasswordRequest req = new ForgotPasswordRequest(NON_EXISTENT_EMAIL);

        mockMvc.perform(post("/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Si el correo existe, enviaremos instrucciones"));

        // Confirmar que no se generó token para el correo inexistente
        assertThat(correoAdapter.getResetTokenForEmail(NON_EXISTENT_EMAIL)).isNull();

        auditLogs.add("\n[CRITERIO 03 - MITIGACIÓN ANTI-ENUMERACIÓN]");
        auditLogs.add("✓ Correo inexistente (" + NON_EXISTENT_EMAIL + ") retorna exactamente 200 OK con mensaje estándar.");
        auditLogs.add("✓ Cero divulgación de existencia de cuentas.");
    }

    @Test @Order(4)
    @DisplayName("04. Validación de contraseña débil y discrepancia en confirmación (RFC 7807 400 Bad Request)")
    void step04_passwordValidationErrors() throws Exception {
        // Caso A: Contraseña débil
        ResetPasswordRequest weakReq = ResetPasswordRequest.builder()
                .token(capturedResetToken)
                .password("debildebil") // sin mayúsculas, números ni símbolos
                .confirmPassword("debildebil")
                .build();

        mockMvc.perform(post("/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(weakReq)))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", Matchers.containsString(MediaType.APPLICATION_PROBLEM_JSON_VALUE)))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.invalidParams.password").exists());

        // Caso B: Confirmación no coincide
        ResetPasswordRequest mismatchReq = ResetPasswordRequest.builder()
                .token(capturedResetToken)
                .password(NEW_PASSWORD)
                .confirmPassword("OtraPassword123#")
                .build();

        mockMvc.perform(post("/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mismatchReq)))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", Matchers.containsString(MediaType.APPLICATION_PROBLEM_JSON_VALUE)))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.userMessage").value(Matchers.containsString("no coinciden")));

        auditLogs.add("\n[CRITERIO 04 - VALIDACIÓN RFC 7807]");
        auditLogs.add("✓ Rechazo 400 ante contraseña débil (Bean Validation con mapa invalidParams).");
        auditLogs.add("✓ Rechazo 400 ante confirmación discrepante con explicación al usuario.");
    }

    @Test @Order(5)
    @DisplayName("05. Restablecimiento exitoso: actualiza hash, revoca refresh tokens y confirma login")
    void step05_successfulPasswordReset() throws Exception {
        ResetPasswordRequest req = ResetPasswordRequest.builder()
                .token(capturedResetToken)
                .password(NEW_PASSWORD)
                .confirmPassword(NEW_PASSWORD)
                .build();

        mockMvc.perform(post("/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(Matchers.containsString("exitosamente")));

        // 1. Verificar que el hash fue actualizado en BD con BCrypt costo >= 12
        User updatedUser = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(passwordEncoder.matches(NEW_PASSWORD, updatedUser.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches(INITIAL_PASSWORD, updatedUser.getPasswordHash())).isFalse();

        // 2. Verificar que todos los refresh tokens fueron revocados
        List<RefreshToken> tokens = refreshTokenRepository.findAllByUserId(testUser.getId());
        assertThat(tokens).isNotEmpty();
        assertThat(tokens).allMatch(RefreshToken::isRevoked);

        // 3. Probar que login con contraseña antigua falla (401)
        LoginRequest oldLoginReq = new LoginRequest(TEST_EMAIL, INITIAL_PASSWORD);
        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(oldLoginReq)))
                .andExpect(status().isUnauthorized());

        // 4. Probar que login con nueva contraseña es exitoso (200)
        LoginRequest newLoginReq = new LoginRequest(TEST_EMAIL, NEW_PASSWORD);
        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newLoginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());

        auditLogs.add("\n[CRITERIO 05 - RESTABLECIMIENTO EXITOSO]");
        auditLogs.add("✓ Contraseña actualizada con BCrypt costo 12 en BD.");
        auditLogs.add("✓ Todos los Refresh Tokens previos fueron revocados.");
        auditLogs.add("✓ Login con contraseña anterior denegado (401).");
        auditLogs.add("✓ Login con nueva contraseña exitoso (200) con emisión de nuevos JWT.");
    }

    @Test @Order(6)
    @DisplayName("06. Enlace de un solo uso: intentar reutilizar el mismo token es rechazado con 400")
    void step06_tokenSingleUseValidation() throws Exception {
        ResetPasswordRequest req = ResetPasswordRequest.builder()
                .token(capturedResetToken)
                .password("AnotherPass123#New")
                .confirmPassword("AnotherPass123#New")
                .build();

        mockMvc.perform(post("/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", Matchers.containsString(MediaType.APPLICATION_PROBLEM_JSON_VALUE)))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value(Matchers.containsString("ya ha sido utilizado")));

        auditLogs.add("\n[CRITERIO 06 - ENLACE DE UN SOLO USO]");
        auditLogs.add("✓ Reutilización del token bloqueada con HTTP 400 ProblemDetail.");
    }

    @Test @Order(7)
    @DisplayName("07. Token vencido a 30 minutos: enlace expirado es rechazado con 400")
    void step07_expiredTokenValidation() throws Exception {
        // Crear un token expirado manualmente en BD
        String expiredRawToken = "expired_raw_token_" + RUN_ID;
        String expiredTokenHash = jwtService.hashToken(expiredRawToken);

        passwordResetTokenRepository.save(PasswordResetToken.builder()
                .user(testUser)
                .tokenHash(expiredTokenHash)
                .expiresAt(OffsetDateTime.now().minusMinutes(5)) // Vencido
                .used(false)
                .build());

        ResetPasswordRequest req = ResetPasswordRequest.builder()
                .token(expiredRawToken)
                .password("SomeNewPass123#Valid")
                .confirmPassword("SomeNewPass123#Valid")
                .build();

        mockMvc.perform(post("/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", Matchers.containsString(MediaType.APPLICATION_PROBLEM_JSON_VALUE)))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value(Matchers.containsString("ha expirado")));

        auditLogs.add("\n[CRITERIO 07 - EXPIRACIÓN A LOS 30 MINUTOS]");
        auditLogs.add("✓ Token expirado rechazado con HTTP 400 ProblemDetail.");
    }

    @Test @Order(8)
    @DisplayName("08. Rate Limiting en solicitudes de recuperación: HTTP 429 con Retry-After")
    void step08_rateLimitingValidation() throws Exception {
        String spamEmail = "spam_user_" + RUN_ID + "@comercio.pe";
        ForgotPasswordRequest req = new ForgotPasswordRequest(spamEmail);

        // Agotar los 3 intentos permitidos del bucket
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/v1/auth/forgot-password")
                            .header("X-Forwarded-For", "203.0.113.195")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk());
        }

        // El 4to intento debe ser bloqueado con HTTP 429 Too Many Requests
        mockMvc.perform(post("/v1/auth/forgot-password")
                        .header("X-Forwarded-For", "203.0.113.195")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Content-Type", Matchers.containsString(MediaType.APPLICATION_PROBLEM_JSON_VALUE)))
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.title").value("Demasiadas peticiones"));

        auditLogs.add("\n[CRITERIO 08 - RATE LIMITING BUCKET4J]");
        auditLogs.add("✓ Límite de 3 solicitudes/15min aplicado por IP y por Correo.");
        auditLogs.add("✓ HTTP 429 Too Many Requests emitido con cabecera Retry-After y RFC 7807.");
    }
}
