package com.pillapago.auth.service;

import com.pillapago.auth.dto.MessageResponse;
import com.pillapago.auth.dto.RegisterRequest;
import com.pillapago.auth.dto.RegisterResponse;
import com.pillapago.auth.dto.ResendVerificationRequest;
import com.pillapago.auth.dto.VerifyEmailRequest;

/**
 * Servicio de registro de comercio y verificación de correo (HU-01).
 */
public interface RegisterService {

    RegisterResponse register(RegisterRequest request);

    MessageResponse verifyEmail(VerifyEmailRequest request);

    MessageResponse resendVerification(ResendVerificationRequest request);
}
