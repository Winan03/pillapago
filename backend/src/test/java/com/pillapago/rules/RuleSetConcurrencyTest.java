package com.pillapago.rules;

import com.pillapago.evaluation.dto.EvaluateTransactionRequest;
import com.pillapago.evaluation.dto.EvaluateTransactionResponse;
import com.pillapago.evaluation.service.TransactionEvaluationService;
import com.pillapago.rules.domain.RuleConditionEntity;
import com.pillapago.rules.domain.RuleEntity;
import com.pillapago.rules.domain.RuleStatus;
import com.pillapago.rules.dto.PublishRuleSetRequest;
import com.pillapago.rules.dto.RuleSetVersionSummaryDto;
import com.pillapago.rules.repository.RuleRepository;
import com.pillapago.rules.service.RuleSetVersionService;
import com.pillapago.shared.crypto.ApiKeyCryptoService;
import com.pillapago.tenant.domain.*;
import com.pillapago.tenant.repository.ApiKeyRepository;
import com.pillapago.tenant.repository.MerchantRepository;
import com.pillapago.tenant.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba de Concurrencia y Atomicidad en Caliente para HU-08:
 * "La activación es atómica: una prueba concurrente (evaluaciones mientras se publica) nunca ve un conjunto parcial."
 */
@SpringBootTest
@ActiveProfiles("test")
public class RuleSetConcurrencyTest {

    @Autowired private TransactionEvaluationService evaluationService;
    @Autowired private RuleSetVersionService versionService;
    @Autowired private RuleRepository ruleRepository;
    @Autowired private MerchantRepository merchantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ApiKeyRepository apiKeyRepository;
    @Autowired private ApiKeyCryptoService cryptoService;

    @Test
    @DisplayName("Activación Atómica Concurrente: Evaluaciones concurrentes durante publish jamás ven estado parcial")
    void testAtomicActivationUnderConcurrentLoad() throws Exception {
        String runId = UUID.randomUUID().toString().substring(0, 8);

        // 1. Setup Comercio y Usuario
        Merchant merchant = merchantRepository.save(Merchant.builder()
                .name("Comercio Concurrente " + runId)
                .uuid(UUID.randomUUID().toString())
                .reviewThreshold(50)
                .rejectThreshold(80)
                .failPolicy("FAIL_CLOSED")
                .dataRetentionMonths(13)
                .build());

        User user = userRepository.save(User.builder()
                .merchant(merchant)
                .username("analyst_" + runId)
                .email("analyst_" + runId + "@pillapago.dev")
                .emailHash("hash_" + runId)
                .passwordHash("$2a$12$dummyHashedPassword12345678901234567890")
                .role(UserRole.RISK_ANALYST)
                .status(UserStatus.ACTIVE)
                .build());

        String rawKey = "pp_live_concurrency_" + runId;
        String keyHash = cryptoService.computeSha256(rawKey);
        ApiKey apiKey = apiKeyRepository.save(ApiKey.builder()
                .merchant(merchant)
                .name("Key Concurrencia")
                .keyHash(keyHash)
                .keyPrefix("pp_live_conc")
                .active(true)
                .build());

        // 2. Crear Regla 1 (monto > 100 -> +30 pts) y publicar v1
        RuleEntity rule1 = RuleEntity.builder()
                .merchant(merchant)
                .name("R1_MontoMayor100_" + runId)
                .points(30)
                .active(true)
                .status(RuleStatus.DRAFT)
                .build();
        rule1.addCondition(RuleConditionEntity.builder()
                .fieldName("amount")
                .operator("GREATER_THAN")
                .targetValue("100.00")
                .valueType("NUMERIC")
                .orderIndex(0)
                .build());
        ruleRepository.save(rule1);

        RuleSetVersionSummaryDto v1 = versionService.publishNewVersion(merchant.getId(), user, new PublishRuleSetRequest("Publicación inicial v1"));
        assertThat(v1.getVersionName()).isEqualTo("v1");

        // 3. Crear Regla 2 (monto > 500 -> +40 pts) en borrador (v2 tendrá 30 + 40 = 70 pts)
        RuleEntity rule2 = RuleEntity.builder()
                .merchant(merchant)
                .name("R2_MontoMayor500_" + runId)
                .points(40)
                .active(true)
                .status(RuleStatus.DRAFT)
                .build();
        rule2.addCondition(RuleConditionEntity.builder()
                .fieldName("amount")
                .operator("GREATER_THAN")
                .targetValue("500.00")
                .valueType("NUMERIC")
                .orderIndex(0)
                .build());
        ruleRepository.save(rule2);

        // 4. Lanzar transacciones concurrentes con monto 1000 (activa R1 y R2)
        int totalRequests = 40;
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalRequests);

        List<EvaluateTransactionResponse> results = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger v1Count = new AtomicInteger(0);
        AtomicInteger v2Count = new AtomicInteger(0);
        AtomicInteger invalidCount = new AtomicInteger(0);

        for (int i = 0; i < totalRequests; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    EvaluateTransactionRequest req = EvaluateTransactionRequest.builder()
                            .transactionId("tx_conc_" + runId + "_" + index)
                            .amount(new BigDecimal("1000.00"))
                            .currency("PEN")
                            .cardFingerprint("fp_conc_" + runId)
                            .build();

                    EvaluateTransactionResponse res = evaluationService.evaluate(req, merchant.getId(), apiKey.getId());
                    results.add(res);

                    if ("v1".equals(res.getRuleSetVersion()) && res.getRiskScore() == 30) {
                        v1Count.incrementAndGet();
                    } else if ("v2".equals(res.getRuleSetVersion()) && res.getRiskScore() == 70) {
                        v2Count.incrementAndGet();
                    } else {
                        invalidCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    invalidCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Liberar hilos de evaluación
        startLatch.countDown();

        // En mitad del tráfico concurrente, publicar v2
        Thread.sleep(5);
        RuleSetVersionSummaryDto v2 = versionService.publishNewVersion(merchant.getId(), user, new PublishRuleSetRequest("Publicación v2 atómica en concurrencia"));
        assertThat(v2.getVersionName()).isEqualTo("v2");

        // Esperar a que terminen todas las evaluaciones
        boolean finished = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).isTrue();
        assertThat(results).hasSize(totalRequests);

        // Aserción estricta de CERO estados intermedios
        System.out.println("Resultados de Concurrencia: v1 evaluadas = " + v1Count.get() + ", v2 evaluadas = " + v2Count.get() + ", inválidas = " + invalidCount.get());
        assertThat(invalidCount.get())
                .as("Ninguna transacción concurrente debe ver un conjunto parcial o score híbrido")
                .isEqualTo(0);

        assertThat(v1Count.get() + v2Count.get()).isEqualTo(totalRequests);
    }
}
