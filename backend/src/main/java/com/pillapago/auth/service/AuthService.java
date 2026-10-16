package com.pillapago.auth.service;

import com.pillapago.auth.dto.LoginRequest;
import com.pillapago.auth.dto.LoginResponse;
import com.pillapago.auth.dto.LogoutRequest;
import com.pillapago.auth.dto.MessageResponse;
import com.pillapago.shared.security.UserPrincipal;

/**
 * Puerto de servicio de autenticación para HU-02 (Login y Logout).
 */
public interface AuthService {

    /**
     * Autentica un usuario contra correo y contraseña con protección de fuerza bruta
     * y mitigación de enumeración de usuarios.
     *
     * @param request Credenciales del usuario
     * @param clientIp Dirección IP del cliente
     * @return LoginResponse con Access JWT (15 min) y Refresh Token
     */
    LoginResponse login(LoginRequest request, String clientIp);

    /**
     * Cierra la sesión invalidando el refresh token en el servidor.
     *
     * @param request DTO con el refresh token a invalidar
     * @param currentUser Usuario autenticado en el contexto de seguridad
     * @return Mensaje de confirmación
     */
    MessageResponse logout(LogoutRequest request, UserPrincipal currentUser);
}
