package com.pillapago.auth.service;

import com.pillapago.auth.dto.ForgotPasswordRequest;
import com.pillapago.auth.dto.MessageResponse;
import com.pillapago.auth.dto.ResetPasswordRequest;

/**
 * Servicio para la gestión del flujo de recuperación y restablecimiento de contraseña (HU-03).
 */
public interface PasswordResetService {

    /**
     * Solicita el restablecimiento de contraseña.
     * Siempre responde con el mensaje estándar sin divulgar si la cuenta existe o no.
     */
    MessageResponse requestPasswordReset(ForgotPasswordRequest request, String clientIp);

    /**
     * Valida el token de un solo uso, actualiza el hash de contraseña y revoca todos los refresh tokens.
     */
    MessageResponse resetPassword(ResetPasswordRequest request);
}
