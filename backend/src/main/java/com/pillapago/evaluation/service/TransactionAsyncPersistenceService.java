package com.pillapago.evaluation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pillapago.evaluation.domain.CaseEntity;
import com.pillapago.evaluation.domain.TransactionEntity;
import com.pillapago.evaluation.domain.TransactionEvaluationEntity;
import com.pillapago.evaluation.dto.EvaluateTransactionRequest;
import com.pillapago.evaluation.dto.EvaluateTransactionResponse;
import com.pillapago.evaluation.dto.RuleReasonDto;
import com.pillapago.evaluation.engine.Decision;
import com.pillapago.evaluation.event.TransactionEvaluatedEvent;
import com.pillapago.evaluation.repository.CaseRepository;
import com.pillapago.evaluation.repository.TransactionEvaluationRepository;
import com.pillapago.evaluation.repository.TransactionRepository;
import com.pillapago.shared.crypto.AssociatedDataBuilder;
import com.pillapago.shared.crypto.FieldEncryptor;
import com.pillapago.tenant.domain.Merchant;
import com.pillapago.tenant.repository.MerchantRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Servicio de persistencia asíncrona fuera del Hot Path utilizando Hilos Virtuales de Java 21 (HU-06c, HU-08).
 * Garantiza que la respuesta HTTP al comercio no espere la escritura en PostgreSQL ni el cifrado Tink.
 */
@Service
@RequiredArgsConstructor
public class TransactionAsyncPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(TransactionAsyncPersistenceService.class);

    private final TransactionRepository transactionRepository;
    private final TransactionEvaluationRepository evaluationRepository;
    private final CaseRepository caseRepository;
    private final MerchantRepository merchantRepository;
    private final FieldEncryptor fieldEncryptor;
    private final ObjectMapper objectMapper;

    @Async("virtualThreadTaskExecutor")
    @EventListener
    @Transactional
    public void handleTransactionEvaluated(TransactionEvaluatedEvent event) {
        UUID merchantId = event.getMerchantId();
        EvaluateTransactionRequest req = event.getRequest();
        EvaluateTransactionResponse res = event.getResponse();

        try {
            Merchant merchant = merchantRepository.findById(merchantId).orElse(null);
            if (merchant == null) {
                log.warn("No se pudo persistir la transacción: comercio {} no encontrado", merchantId);
                return;
            }

            // Evitar duplicados si ya fue persistida (idempotencia en BD)
            if (transactionRepository.existsByMerchantIdAndExternalTransactionId(merchantId, req.getTransactionId())) {
                log.debug("Transacción {} ya persistida previamente para comercio {}", req.getTransactionId(), merchantId);
                return;
            }

            // Cifrado criptográfico con Tink (AEAD AAD vinculado a tabla, columna e ID)
            byte[] customerIdEnc = encryptField(merchantId, req.getCustomerId(), "customer_id", req.getTransactionId());
            byte[] customerEmailEnc = encryptField(merchantId, req.getCustomerEmail(), "customer_email", req.getTransactionId());
            byte[] ipAddressEnc = encryptField(merchantId, req.getIpAddress(), "ip_address", req.getTransactionId());
            byte[] deviceIdEnc = encryptField(merchantId, req.getDeviceId(), "device_id", req.getTransactionId());
            byte[] cardFingerprintEnc = encryptField(merchantId, req.getCardFingerprint(), "card_fingerprint", req.getTransactionId());

            String metadataJson = null;
            if (req.getMetadata() != null && !req.getMetadata().isEmpty()) {
                metadataJson = objectMapper.writeValueAsString(req.getMetadata());
            }

            TransactionEntity transaction = TransactionEntity.builder()
                    .merchant(merchant)
                    .externalTransactionId(req.getTransactionId())
                    .amount(req.getAmount())
                    .currency(req.getCurrency())
                    .decision(res.getDecision())
                    .riskScore(res.getRiskScore())
                    .ruleSetVersion(res.getRuleSetVersion())
                    .degraded(res.isDegraded())
                    .latencyMs(res.getLatencyMs())
                    .customerIdEncrypted(customerIdEnc)
                    .customerEmailEncrypted(customerEmailEnc)
                    .ipAddressEncrypted(ipAddressEnc)
                    .deviceIdEncrypted(deviceIdEnc)
                    .cardFingerprintEncrypted(cardFingerprintEnc)
                    .cardBin(req.getCardBin())
                    .cardLastFour(req.getCardLastFour())
                    .paymentMethod(req.getPaymentMethod())
                    .metadata(metadataJson)
                    .createdAt(event.getTimestamp() != null ? event.getTimestamp() : OffsetDateTime.now())
                    .build();

            TransactionEntity savedTx = transactionRepository.save(transaction);

            // Persistir motivos/reglas activadas
            if (res.getReasons() != null && !res.getReasons().isEmpty()) {
                List<TransactionEvaluationEntity> evalEntities = new ArrayList<>();
                for (RuleReasonDto reason : res.getReasons()) {
                    evalEntities.add(TransactionEvaluationEntity.builder()
                            .transaction(savedTx)
                            .ruleName(reason.getRuleName())
                            .points(reason.getPoints())
                            .description(reason.getDescription())
                            .build());
                }
                evaluationRepository.saveAll(evalEntities);
            }

            // HU-09: Si la decisión es REVIEW, crear automáticamente un caso para analistas de fraude
            if (res.getDecision() == Decision.REVIEW) {
                String priority = res.getRiskScore() >= 75 ? "HIGH" : (res.getRiskScore() >= 50 ? "MEDIUM" : "LOW");
                CaseEntity reviewCase = CaseEntity.builder()
                        .merchant(merchant)
                        .transaction(savedTx)
                        .status("PENDING_REVIEW")
                        .priority(priority)
                        .notes("Caso generado automáticamente por decisión REVIEW (Score: " + res.getRiskScore() + ")")
                        .build();

                caseRepository.save(reviewCase);
                log.info("Caso HU-09 creado para analistas: comercio {}, tx {}", merchantId, req.getTransactionId());
            }

            log.debug("Transacción {} persistida asíncronamente en BD (degraded={}, latency={}ms)",
                    req.getTransactionId(), res.isDegraded(), res.getLatencyMs());
        } catch (Exception ex) {
            log.error("Error al persistir asíncronamente la transacción {} para comercio {}", req.getTransactionId(), merchantId, ex);
        }
    }

    private byte[] encryptField(UUID merchantId, String value, String column, String rowId) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            byte[] aad = AssociatedDataBuilder.build(merchantId, "transactions", column, rowId);
            return fieldEncryptor.encrypt(merchantId, value.trim().getBytes(StandardCharsets.UTF_8), aad);
        } catch (Exception e) {
            log.warn("No se pudo cifrar campo {} de transacción {}: {}", column, rowId, e.getMessage());
            return null;
        }
    }
}
