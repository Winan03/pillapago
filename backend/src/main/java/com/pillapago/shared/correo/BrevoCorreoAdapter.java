package com.pillapago.shared.correo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Adaptador de producción para CorreoPort que envía correos transaccionales a través de la API REST v3 de Brevo.
 * Utiliza HTTP saliente (puerto 443) compatible con Render y entornos cloud con puertos SMTP bloqueados.
 */
@Slf4j
@Component
@Profile("!test")
public class BrevoCorreoAdapter implements CorreoPort {

    private final RestClient restClient;
    private final String apiKey;
    private final String senderEmail;
    private final String senderName;

    public BrevoCorreoAdapter(
            @Value("${brevo.api-key:}") String apiKey,
            @Value("${pillapago.correo.sender-email:jeffryedwin03@gmail.com}") String senderEmail,
            @Value("${pillapago.correo.sender-name:Pillapago Antifraude}") String senderName
    ) {
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.senderEmail = senderEmail != null ? senderEmail.trim() : "jeffryedwin03@gmail.com";
        this.senderName = senderName != null ? senderName.trim() : "Pillapago Antifraude";
        this.restClient = RestClient.builder()
                .baseUrl("https://api.brevo.com/v3")
                .build();
    }

    @Override
    public void enviarCodigoVerificacion(String destinatario, String nombreAdmin, String codigo) {
        if (apiKey.isEmpty()) {
            log.warn("[BREVO] No se configuró 'brevo.api-key'. El código [{}] para {} no se envió por correo real.",
                    codigo, destinatario);
            return;
        }

        String htmlContent = """
                <div style="font-family: Arial, sans-serif; max-width: 550px; margin: 0 auto; padding: 24px; border: 1px solid #e2e8f0; border-radius: 8px;">
                    <h2 style="color: #0f172a; margin-top: 0;">Verificación de Cuenta - Pillapago</h2>
                    <p style="color: #334155;">Hola <strong>%s</strong>,</p>
                    <p style="color: #334155;">Gracias por registrar tu comercio en el Sistema Antifraude Pillapago. Para activar tu cuenta, ingresa el siguiente código de verificación:</p>
                    <div style="background-color: #f1f5f9; padding: 16px; text-align: center; border-radius: 6px; margin: 20px 0;">
                        <span style="font-size: 32px; font-weight: bold; letter-spacing: 8px; color: #2563eb;">%s</span>
                    </div>
                    <p style="color: #64748b; font-size: 13px;">Este código expirará en <strong>15 minutos</strong>. Si no solicitaste este registro, puedes ignorar este mensaje.</p>
                    <hr style="border: none; border-top: 1px solid #e2e8f0; margin: 24px 0;" />
                    <p style="color: #94a3b8; font-size: 11px; text-align: center;">Pillapago - Detección y Prevención de Fraude en Tiempo Real</p>
                </div>
                """.formatted(nombreAdmin, codigo);

        enviarPeticionBrevo(destinatario, nombreAdmin, "Tu código de verificación Pillapago: " + codigo, htmlContent);
    }

    @Override
    public void enviarBienvenida(String destinatario, String nombreAdmin, String merchantName) {
        if (apiKey.isEmpty()) {
            log.warn("[BREVO] No se configuró 'brevo.api-key'. Bienvenida para {} omitida.", destinatario);
            return;
        }

        String htmlContent = """
                <div style="font-family: Arial, sans-serif; max-width: 550px; margin: 0 auto; padding: 24px; border: 1px solid #e2e8f0; border-radius: 8px;">
                    <h2 style="color: #16a34a; margin-top: 0;">¡Cuenta Activada Exitosamente!</h2>
                    <p style="color: #334155;">Hola <strong>%s</strong>,</p>
                    <p style="color: #334155;">Tu cuenta para el comercio <strong>%s</strong> ha sido verificada. Ya puedes acceder al panel de control y comenzar a gestionar reglas antifraude y claves de API.</p>
                    <hr style="border: none; border-top: 1px solid #e2e8f0; margin: 24px 0;" />
                    <p style="color: #94a3b8; font-size: 11px; text-align: center;">Pillapago - Detección y Prevención de Fraude en Tiempo Real</p>
                </div>
                """.formatted(nombreAdmin, merchantName);

        enviarPeticionBrevo(destinatario, nombreAdmin, "¡Bienvenido a Pillapago! - Cuenta Activada", htmlContent);
    }

    private void enviarPeticionBrevo(String destinatario, String nombreDestinatario, String asunto, String htmlContent) {
        try {
            Map<String, Object> payload = Map.of(
                    "sender", Map.of("name", senderName, "email", senderEmail),
                    "to", List.of(Map.of("email", destinatario, "name", nombreDestinatario != null ? nombreDestinatario : "")),
                    "subject", asunto,
                    "htmlContent", htmlContent
            );

            restClient.post()
                    .uri("/smtp/email")
                    .header("api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();

            log.info("[BREVO] Correo enviado exitosamente a: {}", destinatario);
        } catch (Exception e) {
            log.error("[BREVO] Error al enviar correo transaccional a {}: {}", destinatario, e.getMessage());
            // No propagamos la excepción para no abortar el flujo de negocio si la red externa falla momentáneamente
        }
    }
}
