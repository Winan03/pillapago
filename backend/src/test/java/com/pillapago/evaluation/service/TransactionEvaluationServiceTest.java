package com.pillapago.evaluation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pillapago.evaluation.dto.EvaluateTransactionRequest;
import com.pillapago.evaluation.dto.EvaluateTransactionResponse;
import com.pillapago.evaluation.dto.IdempotencyResult;
import com.pillapago.evaluation.engine.Decision;
import com.pillapago.rules.domain.RuleConditionEntity;
import com.pillapago.rules.domain.RuleEntity;
import com.pillapago.rules.domain.RuleStatus;
import com.pillapago.rules.repository.RuleRepository;
import com.pillapago.shared.exception.IdempotencyConflictException;
import com.pillapago.shared.exception.RateLimitExceededException;
import com.pillapago.tenant.domain.Merchant;
import com.pillapago.tenant.repository.MerchantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionEvaluationServiceTest {

    @Mock
    private ApiKeyRateLimiterService rateLimiterService;
    @Mock
    private RedisIdempotencyService idempotencyService;
    @Mock
    private RedisVelocityService velocityService;
    @Mock
    private RuleRepository ruleRepository;
    @Mock
    private MerchantRepository merchantRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ObjectMapper objectMapper;
    private TransactionEvaluationService evaluationService;

    private UUID merchantId;
    private UUID apiKeyId;
    private Merchant merchant;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        merchantId = UUID.randomUUID();
        apiKeyId = UUID.randomUUID();

        merchant = Merchant.builder()
                .id(merchantId)
                .name("Comercio Test")
                .uuid("test-merchant-uuid")
                .reviewThreshold(50)
                .rejectThreshold(80)
                .failPolicy("FAIL_CLOSED")
                .build();

        evaluationService = new TransactionEvaluationService(
                rateLimiterService,
                idempotencyService,
                velocityService,
                ruleRepository,
                merchantRepository,
                eventPublisher,
                objectMapper,
                Executors.newVirtualThreadPerTaskExecutor()
        );
    }

    @Test
    @DisplayName("Debe aprobar transacción con bajo riesgo (Score = 0) y degraded = false")
    void shouldApproveLowRiskTransaction() {
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(idempotencyService.processIdempotency(eq(merchantId), anyString(), anyString()))
                .thenReturn(IdempotencyResult.acquired("tx_001"));
        when(ruleRepository.findAllByMerchantIdWithConditions(merchantId)).thenReturn(Collections.emptyList());

        EvaluateTransactionRequest request = EvaluateTransactionRequest.builder()
                .transactionId("tx_001")
                .amount(new BigDecimal("100.00"))
                .currency("PEN")
                .build();

        EvaluateTransactionResponse response = evaluationService.evaluate(request, merchantId, apiKeyId);

        assertThat(response).isNotNull();
        assertThat(response.getDecision()).isEqualTo(Decision.APPROVE);
        assertThat(response.getRiskScore()).isEqualTo(0);
        assertThat(response.isDegraded()).isFalse();
        verify(eventPublisher, times(1)).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("Debe evaluar reglas y emitir REVIEW cuando supera reviewThreshold (50)")
    void shouldReviewWhenScoreExceedsThreshold() {
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(idempotencyService.processIdempotency(eq(merchantId), anyString(), anyString()))
                .thenReturn(IdempotencyResult.acquired("tx_002"));

        RuleConditionEntity cond = RuleConditionEntity.builder()
                .fieldName("amount")
                .operator(">")
                .targetValue("500")
                .build();

        RuleEntity rule = RuleEntity.builder()
                .id(UUID.randomUUID())
                .name("Monto Mayor a 500")
                .points(60)
                .active(true)
                .status(RuleStatus.PUBLISHED)
                .conditions(List.of(cond))
                .build();

        when(ruleRepository.findAllByMerchantIdWithConditions(merchantId)).thenReturn(List.of(rule));

        EvaluateTransactionRequest request = EvaluateTransactionRequest.builder()
                .transactionId("tx_002")
                .amount(new BigDecimal("600.00"))
                .currency("PEN")
                .build();

        EvaluateTransactionResponse response = evaluationService.evaluate(request, merchantId, apiKeyId);

        assertThat(response).isNotNull();
        assertThat(response.getDecision()).isEqualTo(Decision.REVIEW);
        assertThat(response.getRiskScore()).isEqualTo(60);
        assertThat(response.getReasons()).hasSize(1);
        assertThat(response.isDegraded()).isFalse();
    }

    @Test
    @DisplayName("Debe retornar respuesta en caché si la transacción ya fue completada (Idempotencia Hit)")
    void shouldReturnCachedResponseOnIdempotencyHit() throws Exception {
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));

        EvaluateTransactionResponse cachedResponse = EvaluateTransactionResponse.builder()
                .transactionId("tx_cached")
                .decision(Decision.APPROVE)
                .riskScore(10)
                .degraded(false)
                .latencyMs(15)
                .build();

        String cachedJson = objectMapper.writeValueAsString(cachedResponse);

        when(idempotencyService.processIdempotency(eq(merchantId), eq("tx_cached"), anyString()))
                .thenReturn(IdempotencyResult.completed("tx_cached", cachedJson));

        EvaluateTransactionRequest request = EvaluateTransactionRequest.builder()
                .transactionId("tx_cached")
                .amount(new BigDecimal("100.00"))
                .currency("PEN")
                .build();

        EvaluateTransactionResponse response = evaluationService.evaluate(request, merchantId, apiKeyId);

        assertThat(response).isNotNull();
        assertThat(response.getTransactionId()).isEqualTo("tx_cached");
        assertThat(response.getDecision()).isEqualTo(Decision.APPROVE);
        // No debe evaluar reglas al ser un hit de caché
        verify(ruleRepository, never()).findAllByMerchantIdWithConditions(any());
    }

    @Test
    @DisplayName("Debe propagar IdempotencyConflictException (HTTP 409) si el payload difiere")
    void shouldThrowOnIdempotencyConflict() {
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(idempotencyService.processIdempotency(eq(merchantId), eq("tx_conflict"), anyString()))
                .thenThrow(new IdempotencyConflictException("tx_conflict", "Conflicto", "Datos alterados"));

        EvaluateTransactionRequest request = EvaluateTransactionRequest.builder()
                .transactionId("tx_conflict")
                .amount(new BigDecimal("100.00"))
                .currency("PEN")
                .build();

        assertThatThrownBy(() -> evaluationService.evaluate(request, merchantId, apiKeyId))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    @DisplayName("Debe aplicar FAIL_CLOSED (REVIEW con degraded = true) si ocurre timeout o falla")
    void shouldApplyFailClosedOnResilienceFallback() {
        merchant.setFailPolicy("FAIL_CLOSED");
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(idempotencyService.processIdempotency(eq(merchantId), anyString(), anyString()))
                .thenReturn(IdempotencyResult.acquired("tx_fail_closed"));

        // Simular falla en ruleRepository
        when(ruleRepository.findAllByMerchantIdWithConditions(merchantId))
                .thenThrow(new RuntimeException("Simulated database timeout or failure"));

        EvaluateTransactionRequest request = EvaluateTransactionRequest.builder()
                .transactionId("tx_fail_closed")
                .amount(new BigDecimal("100.00"))
                .currency("PEN")
                .build();

        EvaluateTransactionResponse response = evaluationService.evaluate(request, merchantId, apiKeyId);

        assertThat(response).isNotNull();
        assertThat(response.isDegraded()).isTrue();
        assertThat(response.getDecision()).isEqualTo(Decision.REVIEW);
    }

    @Test
    @DisplayName("Debe aplicar FAIL_OPEN (APPROVE con degraded = true) si el comercio está configurado FAIL_OPEN")
    void shouldApplyFailOpenOnResilienceFallback() {
        merchant.setFailPolicy("FAIL_OPEN");
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(idempotencyService.processIdempotency(eq(merchantId), anyString(), anyString()))
                .thenReturn(IdempotencyResult.acquired("tx_fail_open"));

        when(ruleRepository.findAllByMerchantIdWithConditions(merchantId))
                .thenThrow(new RuntimeException("Simulated timeout or failure"));

        EvaluateTransactionRequest request = EvaluateTransactionRequest.builder()
                .transactionId("tx_fail_open")
                .amount(new BigDecimal("100.00"))
                .currency("PEN")
                .build();

        EvaluateTransactionResponse response = evaluationService.evaluate(request, merchantId, apiKeyId);

        assertThat(response).isNotNull();
        assertThat(response.isDegraded()).isTrue();
        assertThat(response.getDecision()).isEqualTo(Decision.APPROVE);
    }

    @Test
    @DisplayName("Debe rechazar con RateLimitExceededException si el API Key supera su cuota")
    void shouldRejectWhenRateLimitExceeded() {
        doThrow(new RateLimitExceededException("Cuota superada", 1L))
                .when(rateLimiterService).checkRateLimit(apiKeyId);

        EvaluateTransactionRequest request = EvaluateTransactionRequest.builder()
                .transactionId("tx_rate_limit")
                .amount(new BigDecimal("100.00"))
                .currency("PEN")
                .build();

        assertThatThrownBy(() -> evaluationService.evaluate(request, merchantId, apiKeyId))
                .isInstanceOf(RateLimitExceededException.class);
    }
}
