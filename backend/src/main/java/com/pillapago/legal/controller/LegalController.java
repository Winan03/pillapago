package com.pillapago.legal.controller;

import com.pillapago.legal.dto.LegalTermsResponse;
import com.pillapago.legal.dto.LegalTermsResponse.LegalClauseDto;
import com.pillapago.legal.dto.LegalTermsResponse.PciDssStatementDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controlador para la consulta pública de Términos, Condiciones y Políticas de Privacidad y Antifraude (T&C).
 */
@RestController
@RequestMapping("/v1/legal")
public class LegalController {

    public static final String CURRENT_TERMS_VERSION = "v1.0";

    @GetMapping("/terms")
    public ResponseEntity<LegalTermsResponse> getActiveTerms() {
        LegalTermsResponse response = LegalTermsResponse.builder()
                .version(CURRENT_TERMS_VERSION)
                .effectiveDate("2026-10-01")
                .title("Términos del Servicio, Política de Privacidad y Tratamiento de Datos Antifraude")
                .summary("Al registrar un comercio en Pillapago, el usuario administrador autoriza el procesamiento " +
                        "automatizado de datos transaccionales estrictamente con fines de prevención, mitigación de fraude y contracargos.")
                .pciDssCompliance(PciDssStatementDto.builder()
                        .panStored(false)
                        .cvvStored(false)
                        .encryptionStandard("AES-256-GCM (Google Tink)")
                        .cardDataHandling("Pillapago NUNCA almacena el Número Primario de Cuenta (PAN) completo ni el código de seguridad (CVV). " +
                                "Únicamente procesa y retiene tokens o huellas digitales irreversibles (cardFingerprint / SHA-256) bajo estándar PCI-DSS.")
                        .build())
                .clauses(List.of(
                        LegalClauseDto.builder()
                                .key("FRAUD_TELEMETRY")
                                .title("1. Telemetría y Análisis de Transacciones")
                                .description("Autorización para recolectar, procesar y analizar metadatos transaccionales tales como: " +
                                        "dirección IP, fingerprint del dispositivo (device fingerprint), identificadores de cliente, " +
                                        "montos de compra y velocidades transaccionales con el propósito exclusivo de calcular el puntaje de riesgo.")
                                .mandatory(true)
                                .build(),
                        LegalClauseDto.builder()
                                .key("AUTOMATED_DECISION_MAKING")
                                .title("2. Toma de Decisiones Automatizadas")
                                .description("El comercio reconoce y acepta que el motor antifraude emite veredictos automatizados en tiempo real " +
                                        "(APPROVE, REVIEW, REJECT) basados en reglas heurísticas de velocidad y políticas de umbral configuradas.")
                                .mandatory(true)
                                .build(),
                        LegalClauseDto.builder()
                                .key("DATA_RETENTION_AND_ENCRYPTION")
                                .title("3. Cifrado y Retención de Datos")
                                .description("Todos los datos confidenciales e identificadores personales se resguardan cifrados mediante AES-256-GCM. " +
                                        "El periodo de retención estándar es de trece (13) meses calendario para fines de soporte en disputas de contracargos y auditoría forense.")
                                .mandatory(true)
                                .build(),
                        LegalClauseDto.builder()
                                .key("TENANT_RESPONSIBILITY")
                                .title("4. Responsabilidad y Custodia de Credenciales")
                                .description("El comercio es el custodio responsable de sus credenciales de acceso y de sus Claves de API (X-API-Key). " +
                                        "Cualquier transacción evaluada con una API Key activa se presumirá legítimamente originada por el comercio.")
                                .mandatory(true)
                                .build(),
                        LegalClauseDto.builder()
                                .key("COMMUNICATION_AND_NOTIFICATIONS")
                                .title("5. Notificaciones Operativas y de Seguridad")
                                .description("Consentimiento para recibir correos transaccionales indispensables para la seguridad del servicio, tales como: " +
                                        "activación de cuenta, restablecimiento de contraseñas, alertas de umbrales de fraude y generación de claves.")
                                .mandatory(true)
                                .build()
                ))
                .build();

        return ResponseEntity.ok(response);
    }
}
