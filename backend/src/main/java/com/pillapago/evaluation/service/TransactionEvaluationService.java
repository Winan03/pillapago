package com.pillapago.evaluation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pillapago.evaluation.dto.EvaluateTransactionRequest;
import com.pillapago.evaluation.dto.EvaluateTransactionResponse;
import com.pillapago.evaluation.dto.IdempotencyResult;
import com.pillapago.evaluation.dto.RuleReasonDto;
import com.pillapago.evaluation.engine.*;
import com.pillapago.evaluation.event.TransactionEvaluatedEvent;
import com.pillapago.rules.domain.RuleConditionEntity;
import com.pillapago.rules.domain.RuleEntity;
import com.pillapago.rules.domain.RuleStatus;
import com.pillapago.rules.repository.RuleRepository;
import com.pillapago.shared.exception.IdempotencyConflictException;
import com.pillapago.shared.exception.RedisUnavailableException;
import com.pillapago.shared.exception.ResourceNotFoundException;
import com.pillapago.tenant.domain.Merchant;
import com.pillapago.tenant.repository.MerchantRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;

/**
 * Servicio central del Hot Path de Evaluación de Transacciones (HU-06c).
 *
 * Características críticas:
 * 1. SLA estricto de latencia (< 300 ms): si se supera o falla Redis, aplica FAIL_OPEN / FAIL_CLOSED con degraded = true.
 * 2. Idempotencia y Velocity mediante Redis.
 * 3. Rate limiting por API Key mediante Bucket4j.
 * 4. Cero I/O en PostgreSQL en el Hot Path: persistencia asíncrona mediante Hilos Virtuales Java 21.
 */
@Service
public class TransactionEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(TransactionEvaluationService.class);
    private static final long MAX_HOT_PATH_TIMEOUT_MS = 300L;

    private final ApiKeyRateLimiterService rateLimiterService;
    private final RedisIdempotencyService idempotencyService;
    private final RedisVelocityService velocityService;
    private final RuleRepository ruleRepository;
    private final MerchantRepository merchantRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final Executor virtualThreadTaskExecutor;
    private final RuleEvaluator ruleEvaluator;

    public TransactionEvaluationService(
            ApiKeyRateLimiterService rateLimiterService,
            RedisIdempotencyService idempotencyService,
            RedisVelocityService velocityService,
            RuleRepository ruleRepository,
            MerchantRepository merchantRepository,
            ApplicationEventPublisher eventPublisher,
            ObjectMapper objectMapper,
            @Qualifier("virtualThreadTaskExecutor") Executor virtualThreadTaskExecutor
    ) {
        this.rateLimiterService = rateLimiterService;
        this.idempotencyService = idempotencyService;
        this.velocityService = velocityService;
        this.ruleRepository = ruleRepository;
        this.merchantRepository = merchantRepository;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
        this.virtualThreadTaskExecutor = virtualThreadTaskExecutor;
        this.ruleEvaluator = new DefaultRuleEvaluator();
    }

    /**
     * Evalúa una transacción en el Hot Path antifraude.
     */
    public EvaluateTransactionResponse evaluate(EvaluateTransactionRequest request, UUID merchantId, UUID apiKeyId) {
        long startTime = System.currentTimeMillis();

        // 1. Control de tasa de peticiones (Rate Limiting) por API Key con Bucket4j
        rateLimiterService.checkRateLimit(apiKeyId);

        // 2. Obtener configuración del comercio (umbrales y política de resiliencia)
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Comercio no encontrado con ID: " + merchantId,
                        "El comercio asociado a la clave de API no existe."
                ));

        String canonicalPayload = buildCanonicalPayload(request);

        // 3. Control de Idempotencia en Redis
        try {
            IdempotencyResult idempResult = idempotencyService.processIdempotency(
                    merchantId,
                    request.getTransactionId(),
                    canonicalPayload
            );

            if (idempResult.isCompleted() && idempResult.getCachedResponse().isPresent()) {
                String cachedJson = idempResult.getCachedResponse().get();
                if (!cachedJson.isBlank()) {
                    log.info("Idempotencia HIT para transacción {}", request.getTransactionId());
                    try {
                        EvaluateTransactionResponse cachedResponse = objectMapper.readValue(
                                cachedJson,
                                EvaluateTransactionResponse.class
                        );
                        cachedResponse.setLatencyMs(System.currentTimeMillis() - startTime);
                        return cachedResponse;
                    } catch (Exception e) {
                        log.warn("Fallo al deserializar respuesta idempotente previa, re-evaluando...", e);
                    }
                }
            }
        } catch (IdempotencyConflictException ex) {
            // Conflictos de idempotencia (409) deben propagarse directamente
            throw ex;
        } catch (Exception ex) {
            log.warn("Fallo en Redis al verificar idempotencia: {}. Continuando a evaluación degradada...", ex.getMessage());
            // Si Redis falló en idempotencia, aplicamos la política de resiliencia directamente
            return buildDegradedResponse(merchant, request, "Redis no disponible en chequeo de idempotencia: " + ex.getMessage(), startTime);
        }

        // 4. Ejecución del Hot Path con corte estricto de 300 ms SLA
        EvaluationExecutionResult execResult;
        CompletableFuture<EvaluationExecutionResult> future = CompletableFuture.supplyAsync(
                () -> executeHotPathCore(merchant, request),
                virtualThreadTaskExecutor
        );

        try {
            execResult = future.get(MAX_HOT_PATH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            log.warn("SLA de latencia excedido (>300ms) para tx {}. Aplicando failPolicy {} del comercio {}",
                    request.getTransactionId(), merchant.getFailPolicy(), merchantId);
            future.cancel(true);
            execResult = applyFailPolicy(merchant, "SLA de latencia (>300ms) excedido en evaluación");
        } catch (Exception ex) {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            log.error("Falla en evaluación de tx {}: {}. Aplicando failPolicy {}",
                    request.getTransactionId(), cause.getMessage(), merchant.getFailPolicy());
            execResult = applyFailPolicy(merchant, "Falla en motor o infraestructura: " + cause.getMessage());
        }

        long latencyMs = System.currentTimeMillis() - startTime;

        EvaluateTransactionResponse response = EvaluateTransactionResponse.builder()
                .transactionId(request.getTransactionId())
                .decision(execResult.decision())
                .riskScore(execResult.riskScore())
                .reasons(execResult.reasons())
                .ruleSetVersion("1.0.0")
                .degraded(execResult.degraded())
                .latencyMs(latencyMs)
                .build();

        // 5. Marcar Idempotencia como COMPLETED en Redis (si Redis responde)
        try {
            String responseJson = objectMapper.writeValueAsString(response);
            idempotencyService.markCompleted(merchantId, request.getTransactionId(), responseJson);
        } catch (Exception e) {
            log.warn("No se pudo persistir estado COMPLETED en Redis para idempotencia: {}", e.getMessage());
        }

        // 6. Publicación del evento para persistencia asíncrona post-respuesta (Java 21 Virtual Threads)
        eventPublisher.publishEvent(TransactionEvaluatedEvent.builder()
                .merchantId(merchantId)
                .request(request)
                .response(response)
                .timestamp(OffsetDateTime.now())
                .build());

        return response;
    }

    private EvaluationExecutionResult executeHotPathCore(Merchant merchant, EvaluateTransactionRequest request) {
        UUID merchantId = merchant.getId();
        long now = System.currentTimeMillis();

        // 1. Métricas de velocidad en Redis
        long ipVelocity10m = 0;
        long cardVelocity1h = 0;
        long deviceVelocity1h = 0;

        try {
            if (request.getIpAddress() != null && !request.getIpAddress().isBlank()) {
                ipVelocity10m = velocityService.recordAndGetFrequency(merchantId, "ip_txns", request.getIpAddress(), 600, request.getTransactionId(), now);
            }
            if (request.getCardFingerprint() != null && !request.getCardFingerprint().isBlank()) {
                cardVelocity1h = velocityService.recordAndGetFrequency(merchantId, "card_txns", request.getCardFingerprint(), 3600, request.getTransactionId(), now);
            }
            if (request.getDeviceId() != null && !request.getDeviceId().isBlank()) {
                deviceVelocity1h = velocityService.recordAndGetFrequency(merchantId, "device_txns", request.getDeviceId(), 3600, request.getTransactionId(), now);
            }
        } catch (RedisUnavailableException ex) {
            log.warn("Redis falló al registrar velocidad: {}", ex.getMessage());
            return applyFailPolicy(merchant, "Redis no disponible al calcular velocidad: " + ex.getMessage());
        }

        // 2. Cargar reglas activas del comercio
        List<RuleEntity> ruleEntities = ruleRepository.findAllByMerchantIdWithConditions(merchantId).stream()
                .filter(r -> Boolean.TRUE.equals(r.getActive()) && (r.getStatus() == RuleStatus.PUBLISHED || r.getStatus() == RuleStatus.DRAFT))
                .toList();

        List<Rule> engineRules = convertToEngineRules(ruleEntities);

        // 3. Construir EvaluationContext
        EvaluationContext context = buildEvaluationContext(request, ipVelocity10m, cardVelocity1h, deviceVelocity1h);

        // 4. Umbrales del comercio
        int reviewThreshold = merchant.getReviewThreshold() != null ? merchant.getReviewThreshold() : 50;
        int rejectThreshold = merchant.getRejectThreshold() != null ? merchant.getRejectThreshold() : 80;
        ThresholdConfig thresholds = new ThresholdConfig(reviewThreshold, rejectThreshold);

        // 5. Evaluar con motor de reglas
        RuleEvaluationResult result = ruleEvaluator.evaluate(engineRules, context, thresholds);

        List<RuleReasonDto> reasons = result.reasons().stream()
                .map(r -> RuleReasonDto.builder()
                        .ruleName(r.ruleName())
                        .points(r.points())
                        .description("Regla antifraude activada: " + r.ruleName() + " (+" + r.points() + " pts)")
                        .build())
                .toList();

        return new EvaluationExecutionResult(result.decision(), result.riskScore(), reasons, false);
    }

    private EvaluationContext buildEvaluationContext(
            EvaluateTransactionRequest request,
            long ipVelocity10m,
            long cardVelocity1h,
            long deviceVelocity1h
    ) {
        EvaluationContext.Builder builder = EvaluationContext.builder();

        // Atributos de transacción
        if (request.getAmount() != null) builder.put("amount", request.getAmount());
        if (request.getCurrency() != null) builder.put("currency", request.getCurrency());
        if (request.getPaymentMethod() != null) builder.put("paymentMethod", request.getPaymentMethod());
        if (request.getCardBin() != null) builder.put("cardBin", request.getCardBin());
        if (request.getCardLastFour() != null) builder.put("cardLastFour", request.getCardLastFour());
        if (request.getCardFingerprint() != null) builder.put("cardFingerprint", request.getCardFingerprint());
        if (request.getCustomerEmail() != null) builder.put("customerEmail", request.getCustomerEmail());
        if (request.getCustomerId() != null) builder.put("customerId", request.getCustomerId());
        if (request.getIpAddress() != null) builder.put("ipAddress", request.getIpAddress());
        if (request.getDeviceId() != null) builder.put("deviceId", request.getDeviceId());

        // Métricas de velocidad
        builder.put("ip_txns", ipVelocity10m);
        builder.put("ip_velocity", ipVelocity10m);
        builder.put("ip_txns_10m", ipVelocity10m);
        builder.put("card_txns", cardVelocity1h);
        builder.put("card_velocity", cardVelocity1h);
        builder.put("card_txns_1h", cardVelocity1h);
        builder.put("device_txns", deviceVelocity1h);
        builder.put("device_velocity", deviceVelocity1h);
        builder.put("device_txns_1h", deviceVelocity1h);

        // Metadatos adicionales
        if (request.getMetadata() != null) {
            builder.putAll(request.getMetadata());
        }

        return builder.build();
    }

    private List<Rule> convertToEngineRules(List<RuleEntity> entities) {
        List<Rule> rules = new ArrayList<>();
        for (RuleEntity entity : entities) {
            List<Condition> conditions = new ArrayList<>();
            for (RuleConditionEntity cond : entity.getConditions()) {
                Operator op;
                try {
                    op = Operator.valueOf(cond.getOperator());
                } catch (IllegalArgumentException e) {
                    op = Operator.fromSymbol(cond.getOperator());
                }
                conditions.add(new Condition(cond.getFieldName(), op, cond.getTargetValue()));
            }

            if (!conditions.isEmpty()) {
                rules.add(new Rule(
                        entity.getId().toString(),
                        entity.getName(),
                        entity.getDescription(),
                        conditions,
                        entity.getPoints(),
                        Boolean.TRUE.equals(entity.getActive())
                ));
            }
        }
        return rules;
    }

    private EvaluationExecutionResult applyFailPolicy(Merchant merchant, String reasonMsg) {
        String failPolicy = merchant.getFailPolicy() != null ? merchant.getFailPolicy() : "FAIL_CLOSED";
        boolean isOpen = "FAIL_OPEN".equalsIgnoreCase(failPolicy);

        Decision decision = isOpen ? Decision.APPROVE : Decision.REVIEW;
        int score = isOpen ? 0 : (merchant.getReviewThreshold() != null ? merchant.getReviewThreshold() : 50);

        List<RuleReasonDto> reasons = List.of(
                RuleReasonDto.builder()
                        .ruleName("RESILIENCE_FALLBACK")
                        .points(score)
                        .description(reasonMsg + " (Política aplicada: " + failPolicy + ")")
                        .build()
        );

        return new EvaluationExecutionResult(decision, score, reasons, true);
    }

    private EvaluateTransactionResponse buildDegradedResponse(
            Merchant merchant,
            EvaluateTransactionRequest request,
            String reasonMsg,
            long startTime
    ) {
        EvaluationExecutionResult result = applyFailPolicy(merchant, reasonMsg);
        long latencyMs = System.currentTimeMillis() - startTime;

        EvaluateTransactionResponse response = EvaluateTransactionResponse.builder()
                .transactionId(request.getTransactionId())
                .decision(result.decision())
                .riskScore(result.riskScore())
                .reasons(result.reasons())
                .ruleSetVersion("1.0.0")
                .degraded(true)
                .latencyMs(latencyMs)
                .build();

        eventPublisher.publishEvent(TransactionEvaluatedEvent.builder()
                .merchantId(merchant.getId())
                .request(request)
                .response(response)
                .timestamp(OffsetDateTime.now())
                .build());

        return response;
    }

    private String buildCanonicalPayload(EvaluateTransactionRequest req) {
        try {
            Map<String, Object> map = new TreeMap<>();
            map.put("transactionId", req.getTransactionId());
            map.put("amount", req.getAmount() != null ? req.getAmount().stripTrailingZeros().toPlainString() : "0");
            map.put("currency", req.getCurrency());
            if (req.getCardBin() != null) map.put("cardBin", req.getCardBin());
            if (req.getCardLastFour() != null) map.put("cardLastFour", req.getCardLastFour());
            if (req.getCardFingerprint() != null) map.put("cardFingerprint", req.getCardFingerprint());
            if (req.getCustomerEmail() != null) map.put("customerEmail", req.getCustomerEmail());
            if (req.getCustomerId() != null) map.put("customerId", req.getCustomerId());
            if (req.getIpAddress() != null) map.put("ipAddress", req.getIpAddress());
            if (req.getDeviceId() != null) map.put("deviceId", req.getDeviceId());
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return req.getTransactionId() + ":" + req.getAmount() + ":" + req.getCurrency();
        }
    }

    private record EvaluationExecutionResult(
            Decision decision,
            int riskScore,
            List<RuleReasonDto> reasons,
            boolean degraded
    ) {}
}
