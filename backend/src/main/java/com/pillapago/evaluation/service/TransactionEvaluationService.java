package com.pillapago.evaluation.service;

import com.pillapago.lists.domain.ListEntryType;
import com.pillapago.lists.domain.ListType;
import com.pillapago.lists.service.ListCacheService;
import com.pillapago.shared.crypto.HmacIndexService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pillapago.evaluation.dto.EvaluateTransactionRequest;
import com.pillapago.evaluation.dto.EvaluateTransactionResponse;
import com.pillapago.evaluation.dto.IdempotencyResult;
import com.pillapago.evaluation.dto.RuleReasonDto;
import com.pillapago.evaluation.engine.*;
import com.pillapago.evaluation.event.TransactionEvaluatedEvent;
import com.pillapago.shared.exception.IdempotencyConflictException;
import com.pillapago.shared.exception.RedisUnavailableException;
import com.pillapago.shared.exception.ResourceNotFoundException;
import com.pillapago.tenant.domain.Merchant;
import com.pillapago.tenant.repository.MerchantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;

/**
 * Servicio central del Hot Path de Evaluación de Transacciones (HU-06c, HU-08).
 *
 * Características críticas:
 * 1. SLA estricto de latencia (< 300 ms): si se supera o falla Redis, aplica FAIL_OPEN / FAIL_CLOSED con degraded = true.
 * 2. Idempotencia y Velocity mediante Redis.
 * 3. Rate limiting por API Key mediante Bucket4j.
 * 4. Cero I/O en PostgreSQL en el Hot Path: persistencia asíncrona mediante Hilos Virtuales Java 21.
 * 5. Activación y recarga consistente de reglas en memoria sin downtime (HU-08 vía RuleEngineCacheService).
 */
@Service
public class TransactionEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(TransactionEvaluationService.class);
    private static final long MAX_HOT_PATH_TIMEOUT_MS = 300L;

    private final ApiKeyRateLimiterService rateLimiterService;
    private final RedisIdempotencyService idempotencyService;
    private final RedisVelocityService velocityService;
    private final RuleEngineCacheService ruleEngineCacheService;
    private final MerchantRepository merchantRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final Executor virtualThreadTaskExecutor;
    private final RuleEvaluator ruleEvaluator;
    private final ListCacheService listCacheService;
    private final HmacIndexService hmacIndexService;


        @org.springframework.beans.factory.annotation.Autowired
    public TransactionEvaluationService(
            ApiKeyRateLimiterService rateLimiterService,
            RedisIdempotencyService idempotencyService,
            RedisVelocityService velocityService,
            RuleEngineCacheService ruleEngineCacheService,
            MerchantRepository merchantRepository,
            ApplicationEventPublisher eventPublisher,
            ObjectMapper objectMapper,
            @Qualifier("virtualThreadTaskExecutor") Executor virtualThreadTaskExecutor,
            @org.springframework.context.annotation.Lazy ListCacheService listCacheService,
            @org.springframework.context.annotation.Lazy HmacIndexService hmacIndexService
    ) {
        this.rateLimiterService = rateLimiterService;
        this.idempotencyService = idempotencyService;
        this.velocityService = velocityService;
        this.ruleEngineCacheService = ruleEngineCacheService;
        this.merchantRepository = merchantRepository;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
        this.virtualThreadTaskExecutor = virtualThreadTaskExecutor;
        this.ruleEvaluator = new DefaultRuleEvaluator();
        this.listCacheService = listCacheService;
        this.hmacIndexService = hmacIndexService;
    }

    public TransactionEvaluationService(
            ApiKeyRateLimiterService rateLimiterService,
            RedisIdempotencyService idempotencyService,
            RedisVelocityService velocityService,
            RuleEngineCacheService ruleEngineCacheService,
            MerchantRepository merchantRepository,
            ApplicationEventPublisher eventPublisher,
            ObjectMapper objectMapper,
            Executor virtualThreadTaskExecutor
    ) {
        this(rateLimiterService, idempotencyService, velocityService, ruleEngineCacheService,
                merchantRepository, eventPublisher, objectMapper, virtualThreadTaskExecutor, null, null);
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
        // 2.1 Verificación prioritaria de listas de bloqueo y confianza en memoria O(1) (HU-13 / RF-09)
        ListCacheService.ListCheckResult listResult = checkInMemoryLists(merchantId, request);
        if (listResult.matched()) {
            long latencyMs = System.currentTimeMillis() - startTime;
            if (listResult.listType() == ListType.BLOCK) {
                // Criterio 3: Una coincidencia en BLOCK devuelve REJECT inmediato en la evaluación
                log.warn("[HOT-PATH-REJECT] Tx {} rechazada de inmediato por BLOCKLIST [{}] motivo: {}",
                        request.getTransactionId(), listResult.entryType(), listResult.reason());
                EvaluateTransactionResponse rejectResponse = EvaluateTransactionResponse.builder()
                        .transactionId(request.getTransactionId())
                        .decision(Decision.REJECT)
                        .riskScore(100)
                        .reasons(List.of(RuleReasonDto.builder()
                                .ruleName("BLOCKLIST_" + listResult.entryType().name())
                                .points(100)
                                .description("Rechazo inmediato por lista de bloqueo (" + listResult.entryType() + "): " + listResult.reason())
                                .build()))
                        .ruleSetVersion("list-block")
                        .degraded(false)
                        .latencyMs(latencyMs)
                        .build();

                eventPublisher.publishEvent(TransactionEvaluatedEvent.builder()
                        .merchantId(merchantId)
                        .request(request)
                        .response(rejectResponse)
                        .timestamp(OffsetDateTime.now())
                        .build());
                return rejectResponse;
            } else if (listResult.listType() == ListType.ALLOW) {
                // Coincidencia en ALLOW: Whitelist Bypass emite APPROVE inmediato (Score 0)
                log.info("[HOT-PATH-APPROVE] Tx {} aprobada de inmediato por ALLOWLIST [{}] motivo: {}",
                        request.getTransactionId(), listResult.entryType(), listResult.reason());
                EvaluateTransactionResponse approveResponse = EvaluateTransactionResponse.builder()
                        .transactionId(request.getTransactionId())
                        .decision(Decision.APPROVE)
                        .riskScore(0)
                        .reasons(List.of(RuleReasonDto.builder()
                                .ruleName("ALLOWLIST_" + listResult.entryType().name())
                                .points(0)
                                .description("Aprobación inmediata por lista de confianza (" + listResult.entryType() + "): " + listResult.reason())
                                .build()))
                        .ruleSetVersion("list-allow")
                        .degraded(false)
                        .latencyMs(latencyMs)
                        .build();

                eventPublisher.publishEvent(TransactionEvaluatedEvent.builder()
                        .merchantId(merchantId)
                        .request(request)
                        .response(approveResponse)
                        .timestamp(OffsetDateTime.now())
                        .build());
                return approveResponse;
            }
        }

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
                        return cachedResponse;
                    } catch (Exception e) {
                        log.warn("Error al deserializar respuesta cacheada en Redis: {}", e.getMessage());
                    }
                }
            }
        } catch (IdempotencyConflictException e) {
            log.warn("Conflicto de idempotencia detectado para tx {}", request.getTransactionId());
            throw e;
        } catch (RedisUnavailableException e) {
            log.warn("Redis no disponible en verificación de idempotencia, aplicando failover: {}", e.getMessage());
            return buildDegradedResponse(merchant, request, "Falla en capa de idempotencia Redis", startTime);
        }

        // 4. Ejecución del Hot Path con corte estricto de tiempo a 300 ms (CompletableFuture + Hilos Virtuales)
        CompletableFuture<EvaluationExecutionResult> future = CompletableFuture.supplyAsync(
                () -> executeEvaluation(merchant, request),
                virtualThreadTaskExecutor
        );

        EvaluationExecutionResult result;
        try {
            result = future.get(MAX_HOT_PATH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.error("Evaluación excedió el SLA de 300 ms para tx {}. Aplicando failover.", request.getTransactionId());
            future.cancel(true);
            return buildDegradedResponse(merchant, request, "SLA de evaluación superó los 300 ms", startTime);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Hilo de evaluación interrumpido para tx {}", request.getTransactionId());
            return buildDegradedResponse(merchant, request, "Evaluación interrumpida", startTime);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.error("Error durante la evaluación de la tx {}: {}", request.getTransactionId(), cause.getMessage(), cause);
            return buildDegradedResponse(merchant, request, "Error interno en motor: " + cause.getMessage(), startTime);
        }

        long latencyMs = System.currentTimeMillis() - startTime;

        EvaluateTransactionResponse response = EvaluateTransactionResponse.builder()
                .transactionId(request.getTransactionId())
                .decision(result.decision())
                .riskScore(result.riskScore())
                .reasons(result.reasons())
                .ruleSetVersion(result.ruleSetVersion() != null ? result.ruleSetVersion() : "v0-draft")
                .degraded(result.degraded())
                .latencyMs(latencyMs)
                .build();

        // 5. Guardar respuesta final en Redis para futuras peticiones idempotentes
        try {
            String responseJson = objectMapper.writeValueAsString(response);
            idempotencyService.markCompleted(merchantId, request.getTransactionId(), responseJson);
        } catch (Exception e) {
            log.warn("No se pudo cachear respuesta en Redis para tx {}: {}", request.getTransactionId(), e.getMessage());
        }

        // 6. Publicar evento asíncrono para persistencia en PostgreSQL sin bloquear el Hot Path
        eventPublisher.publishEvent(TransactionEvaluatedEvent.builder()
                .merchantId(merchantId)
                .request(request)
                .response(response)
                .timestamp(OffsetDateTime.now())
                .build());

        return response;
    }

    private EvaluationExecutionResult executeEvaluation(
            Merchant merchant,
            EvaluateTransactionRequest request
    ) {
        UUID merchantId = merchant.getId();
        long now = System.currentTimeMillis();

        // 1. Obtener conjunto de reglas activo en memoria (Caché O(1) con AtomicReference - HU-08)
        RuleEngineCacheService.ActiveRuleSet activeRuleSet = ruleEngineCacheService.getActiveRuleSet(merchantId);

        // 1. Métricas de velocidad en Redis
        long ipVelocity10m = 0;
        long cardVelocity1h = 0;
        long deviceVelocity1h = 0;

        try {
            if (request.getIpAddress() != null && !request.getIpAddress().isBlank()) {
                ipVelocity10m = velocityService.recordAndGetFrequency(
                        merchantId, "ip_txns", request.getIpAddress(), 600, request.getTransactionId(), now
                );
            }
            if (request.getCardFingerprint() != null && !request.getCardFingerprint().isBlank()) {
                cardVelocity1h = velocityService.recordAndGetFrequency(
                        merchantId, "card_txns", request.getCardFingerprint(), 3600, request.getTransactionId(), now
                );
            }
            if (request.getDeviceId() != null && !request.getDeviceId().isBlank()) {
                deviceVelocity1h = velocityService.recordAndGetFrequency(
                        merchantId, "device_txns", request.getDeviceId(), 3600, request.getTransactionId(), now
                );
            }
        } catch (RedisUnavailableException e) {
            log.warn("Falla en Redis al calcular velocidad para tx {}: {}", request.getTransactionId(), e.getMessage());
            return applyFailPolicy(merchant, "Falla en servicio de velocidad Redis");
        }

        // 2. Construir EvaluationContext para el motor de reglas
        EvaluationContext context = buildEvaluationContext(request, ipVelocity10m, cardVelocity1h, deviceVelocity1h);

        // 3. Reglas en memoria y umbrales
        List<Rule> rules = activeRuleSet.compiledRules();
        int reviewThreshold = merchant.getReviewThreshold() != null ? merchant.getReviewThreshold() : 50;
        int rejectThreshold = merchant.getRejectThreshold() != null ? merchant.getRejectThreshold() : 80;
        ThresholdConfig thresholdConfig = new ThresholdConfig(reviewThreshold, rejectThreshold);

        // 4. Evaluar con motor antifraude
        RuleEvaluationResult evalResult = ruleEvaluator.evaluate(rules, context, thresholdConfig);

        List<RuleReasonDto> reasons = evalResult.reasons().stream()
                .map(r -> RuleReasonDto.builder()
                        .ruleName(r.ruleName())
                        .points(r.points())
                        .description("Regla activada: " + r.ruleName() + " (+" + r.points() + " pts)")
                        .build())
                .toList();

        return new EvaluationExecutionResult(
                evalResult.decision(),
                evalResult.riskScore(),
                reasons,
                activeRuleSet.versionName(),
                false
        );
    }

    private EvaluationContext buildEvaluationContext(
            EvaluateTransactionRequest request,
            long ipVelocity10m,
            long cardVelocity1h,
            long deviceVelocity1h
    ) {
        EvaluationContext.Builder builder = EvaluationContext.builder();

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

        builder.put("ip_txns", ipVelocity10m);
        builder.put("ip_velocity", ipVelocity10m);
        builder.put("ip_txns_10m", ipVelocity10m);
        builder.put("card_txns", cardVelocity1h);
        builder.put("card_velocity", cardVelocity1h);
        builder.put("card_txns_1h", cardVelocity1h);
        builder.put("device_txns", deviceVelocity1h);
        builder.put("device_velocity", deviceVelocity1h);
        builder.put("device_txns_1h", deviceVelocity1h);

        if (request.getMetadata() != null) {
            builder.putAll(request.getMetadata());
        }

        return builder.build();
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

        return new EvaluationExecutionResult(decision, score, reasons, "FAILOVER", true);
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
                .ruleSetVersion("FAILOVER")
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
            String ruleSetVersion,
            boolean degraded
    ) {}

    private ListCacheService.ListCheckResult checkInMemoryLists(UUID merchantId, EvaluateTransactionRequest request) {
        if (listCacheService == null || hmacIndexService == null) {
            return ListCacheService.ListCheckResult.noMatch();
        }
        Map<ListEntryType, String> hashedIdentifiers = new EnumMap<>(ListEntryType.class);
        if (request.getIpAddress() != null && !request.getIpAddress().isBlank()) {
            hashedIdentifiers.put(ListEntryType.IP, hmacIndexService.computeBlindIndex(merchantId, request.getIpAddress().trim()));
        }
        if (request.getDeviceId() != null && !request.getDeviceId().isBlank()) {
            hashedIdentifiers.put(ListEntryType.DEVICE, hmacIndexService.computeBlindIndex(merchantId, request.getDeviceId().trim()));
        }
        if (request.getCustomerEmail() != null && !request.getCustomerEmail().isBlank()) {
            hashedIdentifiers.put(ListEntryType.EMAIL, hmacIndexService.computeBlindIndex(merchantId, request.getCustomerEmail().trim()));
        }
        if (request.getCardFingerprint() != null && !request.getCardFingerprint().isBlank()) {
            hashedIdentifiers.put(ListEntryType.CARD_FINGERPRINT, hmacIndexService.computeBlindIndex(merchantId, request.getCardFingerprint().trim()));
        }
        return listCacheService.evaluateTransaction(merchantId, hashedIdentifiers);
    }
}