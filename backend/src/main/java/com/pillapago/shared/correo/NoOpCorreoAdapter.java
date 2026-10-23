package com.pillapago.shared.correo;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Adaptador de prueba y desarrollo local para CorreoPort.
 * Captura en memoria el último código o token generado para verificación sin dependencias de red externas.
 */
@Slf4j
@Component
@Profile("test")
@Primary
public class NoOpCorreoAdapter implements CorreoPort {

    @Getter
    private volatile String lastRecipient;

    @Getter
    private volatile String lastAdminName;

    @Getter
    private volatile String lastSentCode;

    @Getter
    private volatile String lastResetToken;

    private final ConcurrentHashMap<String, String> codesByEmail = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> resetTokensByEmail = new ConcurrentHashMap<>();

    @Override
    public void enviarCodigoVerificacion(String destinatario, String nombreAdmin, String codigo) {
        this.lastRecipient = destinatario;
        this.lastAdminName = nombreAdmin;
        this.lastSentCode = codigo;
        this.codesByEmail.put(destinatario.trim().toLowerCase(), codigo);
        log.info("[NOOP-EMAIL] Código de verificación para {} ({}): [{}]", destinatario, nombreAdmin, codigo);
    }

    @Override
    public void enviarBienvenida(String destinatario, String nombreAdmin, String merchantName) {
        log.info("[NOOP-EMAIL] Bienvenida enviada a {} para comercio {}", destinatario, merchantName);
    }

    @Override
    public void enviarRecuperacionContrasena(String destinatario, String nombreUsuario, String token) {
        this.lastRecipient = destinatario;
        this.lastAdminName = nombreUsuario;
        this.lastResetToken = token;
        this.resetTokensByEmail.put(destinatario.trim().toLowerCase(), token);
        log.info("[NOOP-EMAIL] Token de recuperación de contraseña para {} ({}): [{}]", destinatario, nombreUsuario, token);
    }

    public String getCodeForEmail(String email) {
        return codesByEmail.get(email.trim().toLowerCase());
    }

    public String getResetTokenForEmail(String email) {
        return resetTokensByEmail.get(email.trim().toLowerCase());
    }

    public void clear() {
        this.lastRecipient = null;
        this.lastAdminName = null;
        this.lastSentCode = null;
        this.lastResetToken = null;
        this.codesByEmail.clear();
        this.resetTokensByEmail.clear();
    }
}
