package com.pillapago.shared.correo;

/**
 * Puerto de abstracción hexagonal para el envío transaccional de correos electrónicos.
 * Permite desacoplar el flujo de registro de la infraestructura de entrega (Brevo REST API, SMTP o NoOp en tests).
 */
public interface CorreoPort {

    /**
     * Envía el código numérico de 6 dígitos para la activación de cuenta.
     */
    void enviarCodigoVerificacion(String destinatario, String nombreAdmin, String codigo);

    /**
     * Envía correo de bienvenida y confirmación de activación de cuenta.
     */
    void enviarBienvenida(String destinatario, String nombreAdmin, String merchantName);
}
