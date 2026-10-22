package com.pillapago.shared.correo;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Adaptador de prueba y desarrollo local para CorreoPort.
 * Captura en memoria el último código generado para verificación sin dependencias de red externas.
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

    private final ConcurrentHashMap<String, String> codesByEmail = new ConcurrentHashMap<>();

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

    public String getCodeForEmail(String email) {
        return codesByEmail.get(email.trim().toLowerCase());
    }

    public void clear() {
        this.lastRecipient = null;
        this.lastAdminName = null;
        this.lastSentCode = null;
        this.codesByEmail.clear();
    }
}
