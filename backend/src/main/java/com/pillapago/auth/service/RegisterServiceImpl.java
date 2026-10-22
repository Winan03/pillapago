package com.pillapago.auth.service;

import com.pillapago.auth.domain.EmailVerificationCode;
import com.pillapago.auth.dto.*;
import com.pillapago.auth.repository.EmailVerificationCodeRepository;
import com.pillapago.shared.correo.CorreoPort;
import com.pillapago.shared.exception.*;
import com.pillapago.shared.security.JwtService;
import com.pillapago.tenant.domain.Merchant;
import com.pillapago.tenant.domain.User;
import com.pillapago.tenant.domain.UserRole;
import com.pillapago.tenant.domain.UserStatus;
import com.pillapago.tenant.repository.MerchantRepository;
import com.pillapago.tenant.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegisterServiceImpl implements RegisterService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int CODE_TTL_MINUTES = 15;

    private final UserRepository userRepository;
    private final MerchantRepository merchantRepository;
    private final EmailVerificationCodeRepository verificationCodeRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CorreoPort correoPort;

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        // 1. Validar coincidencia de contraseña y confirmación
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new PasswordMismatchException("Las contraseñas no coinciden");
        }

        String normalizedEmail = request.getEmail().trim().toLowerCase();
        String emailHash = jwtService.hashToken(normalizedEmail);

        // 2. Validar unicidad global del correo electrónico
        if (userRepository.existsByEmailHash(emailHash) || userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            log.warn("Intento de registro con correo ya existente: {}", normalizedEmail);
            throw new EmailAlreadyRegisteredException("El correo electrónico ya se encuentra registrado en el sistema");
        }

        // 3. Crear comercio (Tenant)
        Merchant merchant = Merchant.builder()
                .name(request.getMerchantName().trim())
                .uuid(UUID.randomUUID().toString())
                .reviewThreshold(50)
                .rejectThreshold(80)
                .failPolicy("FAIL_CLOSED")
                .dataRetentionMonths(13)
                .build();
        merchant = merchantRepository.save(merchant);

        // 4. Crear usuario administrador del comercio en estado PENDING_VERIFICATION
        User user = User.builder()
                .merchant(merchant)
                .username(request.getAdminName().trim())
                .email(normalizedEmail)
                .emailHash(emailHash)
                .passwordHash(passwordEncoder.encode(request.getPassword())) // BCrypt costo >= 12
                .role(UserRole.MERCHANT_ADMIN)
                .status(UserStatus.PENDING_VERIFICATION)
                .mfaEnabled(false)
                .build();
        user = userRepository.save(user);

        // 5. Generar y almacenar código de 6 dígitos numéricos
        String rawCode = generate6DigitCode();
        String codeHash = jwtService.hashToken(rawCode);

        EmailVerificationCode codeEntity = EmailVerificationCode.builder()
                .user(user)
                .codeHash(codeHash)
                .expiresAt(OffsetDateTime.now().plusMinutes(CODE_TTL_MINUTES))
                .attempts(0)
                .used(false)
                .build();
        verificationCodeRepository.save(codeEntity);

        // 6. Enviar código a través del puerto de correo
        correoPort.enviarCodigoVerificacion(normalizedEmail, user.getUsername(), rawCode);

        log.info("Comercio registrado exitosamente: {} [ID: {}], Usuario: {} [Status: PENDING_VERIFICATION]",
                merchant.getName(), merchant.getId(), user.getEmail());

        return RegisterResponse.builder()
                .message("Registro exitoso. Se ha enviado un código de 6 dígitos a tu correo electrónico para activar la cuenta.")
                .maskedEmail(maskEmail(normalizedEmail))
                .merchantId(merchant.getId())
                .status(user.getStatus().name())
                .build();
    }

    @Override
    @Transactional(noRollbackFor = {InvalidVerificationCodeException.class, AccountLockedException.class})
    public MessageResponse verifyEmail(VerifyEmailRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        String emailHash = jwtService.hashToken(normalizedEmail);

        User user = userRepository.findByEmailIgnoreCase(normalizedEmail)
                .or(() -> userRepository.findByEmailHash(emailHash))
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró ningún usuario con el correo proporcionado"));

        if (user.getStatus() == UserStatus.ACTIVE) {
            return new MessageResponse("La cuenta ya se encuentra verificada y activa.");
        }

        if (user.getStatus() == UserStatus.DISABLED) {
            throw new AccountLockedException("La cuenta se encuentra inhabilitada. Contacte a soporte.");
        }

        EmailVerificationCode codeEntity = verificationCodeRepository.findTopByUserIdAndUsedFalseOrderByCreatedAtDesc(user.getId())
                .orElseThrow(() -> new InvalidVerificationCodeException("No hay un código de verificación activo para esta cuenta. Por favor solicita uno nuevo."));

        // Control de intentos fallidos (máximo 5)
        if (codeEntity.getAttempts() >= MAX_FAILED_ATTEMPTS) {
            log.warn("Cuenta bloqueada por superar {} intentos de verificación: {}", MAX_FAILED_ATTEMPTS, normalizedEmail);
            throw new AccountLockedException("Cuenta bloqueada temporalmente por exceder el número máximo de intentos fallidos (5/5). Solicita un nuevo código de verificación.");
        }

        // Control de tiempo de vida (TTL 15 min)
        if (codeEntity.isExpired()) {
            log.warn("Código de verificación expirado para usuario: {}", normalizedEmail);
            throw new InvalidVerificationCodeException("El código de verificación ha expirado. Por favor solicita un nuevo código.");
        }

        // Verificación criptográfica con hash SHA-256
        String inputHash = jwtService.hashToken(request.getCode().trim());
        if (!codeEntity.getCodeHash().equals(inputHash)) {
            int newAttempts = codeEntity.getAttempts() + 1;
            codeEntity.setAttempts(newAttempts);
            verificationCodeRepository.saveAndFlush(codeEntity);

            int remaining = MAX_FAILED_ATTEMPTS - newAttempts;
            if (remaining <= 0) {
                log.warn("Se agotaron los intentos de verificación para usuario: {}", normalizedEmail);
                throw new AccountLockedException("Cuenta bloqueada temporalmente por exceder el número máximo de intentos fallidos (5/5). Solicita un nuevo código de verificación.");
            }
            throw new InvalidVerificationCodeException("Código de verificación incorrecto. Te quedan " + remaining + " intento(s).");
        }

        // Código válido: activar cuenta
        codeEntity.setUsed(true);
        verificationCodeRepository.save(codeEntity);

        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);

        correoPort.enviarBienvenida(user.getEmail(), user.getUsername(), user.getMerchant().getName());

        log.info("Cuenta activada exitosamente para comercio: {} [Usuario: {}]",
                user.getMerchant().getName(), user.getEmail());

        return new MessageResponse("Correo verificado exitosamente. Tu cuenta está activa y ya puedes iniciar sesión.");
    }

    @Override
    @Transactional
    public MessageResponse resendVerification(ResendVerificationRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        String emailHash = jwtService.hashToken(normalizedEmail);

        User user = userRepository.findByEmailIgnoreCase(normalizedEmail)
                .or(() -> userRepository.findByEmailHash(emailHash))
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró ningún usuario con el correo proporcionado"));

        if (user.getStatus() == UserStatus.ACTIVE) {
            throw new EmailAlreadyRegisteredException("La cuenta ya se encuentra verificada y activa.");
        }

        if (user.getStatus() == UserStatus.DISABLED) {
            throw new AccountLockedException("La cuenta se encuentra inhabilitada. Contacte a soporte.");
        }

        // 1. Invalidar códigos anteriores no utilizados
        verificationCodeRepository.invalidatePendingCodes(user.getId());

        // 2. Generar y registrar nuevo código
        String rawCode = generate6DigitCode();
        String codeHash = jwtService.hashToken(rawCode);

        EmailVerificationCode newCode = EmailVerificationCode.builder()
                .user(user)
                .codeHash(codeHash)
                .expiresAt(OffsetDateTime.now().plusMinutes(CODE_TTL_MINUTES))
                .attempts(0)
                .used(false)
                .build();
        verificationCodeRepository.save(newCode);

        // 3. Enviar correo con nuevo código
        correoPort.enviarCodigoVerificacion(normalizedEmail, user.getUsername(), rawCode);

        log.info("Nuevo código de verificación generado y enviado para usuario: {}", normalizedEmail);

        return new MessageResponse("Nuevo código de verificación enviado exitosamente. Revisa tu correo.");
    }

    private String generate6DigitCode() {
        int code = secureRandom.nextInt(900_000) + 100_000; // Entre 100000 y 999999 inclusive
        return String.valueOf(code);
    }

    private String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return "***" + email.substring(atIndex);
        }
        return email.charAt(0) + "***" + email.substring(atIndex);
    }
}
