package com.pillapago.evaluation;

import com.pillapago.evaluation.domain.CaseEntity;
import com.pillapago.evaluation.domain.CaseResolution;
import com.pillapago.evaluation.domain.CaseStatus;
import com.pillapago.evaluation.domain.TransactionEntity;
import com.pillapago.evaluation.dto.ResolveCaseRequest;
import com.pillapago.evaluation.engine.Decision;
import com.pillapago.evaluation.repository.CaseRepository;
import com.pillapago.evaluation.repository.TransactionRepository;
import com.pillapago.evaluation.service.CaseService;
import com.pillapago.tenant.domain.Merchant;
import com.pillapago.tenant.domain.User;
import com.pillapago.tenant.domain.UserRole;
import com.pillapago.tenant.domain.UserStatus;
import com.pillapago.tenant.repository.MerchantRepository;
import com.pillapago.tenant.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba de Concurrencia para HU-09:
 * Simula dos analistas intentando resolver concurrentemente el mismo caso.
 * Valida el bloqueo optimista de JPA (@Version):
 * - Una petición triunfa actualizando el estado a RESOLVED.
 * - La petición concurrente detecta conflicto de versión y lanza OptimisticLockingFailureException.
 */
@SpringBootTest
@ActiveProfiles("test")
public class CaseOptimisticLockingConcurrencyTest {

    @Autowired
    private CaseService caseService;

    @Autowired
    private CaseRepository caseRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("Concurrencia: Dos analistas resolviendo simultáneamente el mismo caso -> uno triunfa y el otro falla por bloqueo optimista")
    void testConcurrentCaseResolutionOptimisticLocking() throws Exception {
        // 1. Crear Merchant de prueba
        Merchant merchant = merchantRepository.save(Merchant.builder()
                .name("Comercio Concurrencia " + UUID.randomUUID())
                .uuid(UUID.randomUUID().toString())
                .build());

        // 2. Crear dos analistas distintos
        User analyst1 = userRepository.save(User.builder()
                .merchant(merchant)
                .username("analyst_lock_1_" + UUID.randomUUID().toString().substring(0, 8))
                .email("lock1@pillapago.com")
                .emailHash(UUID.randomUUID().toString())
                .passwordHash(passwordEncoder.encode("Password123!"))
                .role(UserRole.RISK_ANALYST)
                .status(UserStatus.ACTIVE)
                .build());

        User analyst2 = userRepository.save(User.builder()
                .merchant(merchant)
                .username("analyst_lock_2_" + UUID.randomUUID().toString().substring(0, 8))
                .email("lock2@pillapago.com")
                .emailHash(UUID.randomUUID().toString())
                .passwordHash(passwordEncoder.encode("Password123!"))
                .role(UserRole.RISK_ANALYST)
                .status(UserStatus.ACTIVE)
                .build());

        // 3. Crear Transacción y Caso en estado PENDING_REVIEW
        TransactionEntity tx = transactionRepository.save(TransactionEntity.builder()
                .merchant(merchant)
                .externalTransactionId("TX_CONC_" + UUID.randomUUID())
                .amount(new BigDecimal("1250.00"))
                .currency("PEN")
                .decision(Decision.REVIEW)
                .riskScore(68)
                .ruleSetVersion("v1")
                .degraded(false)
                .latencyMs(45L)
                .build());

        CaseEntity caseEntity = caseRepository.save(CaseEntity.builder()
                .merchant(merchant)
                .transaction(tx)
                .status(CaseStatus.PENDING_REVIEW)
                .priority("HIGH")
                .version(0)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build());

        UUID caseId = caseEntity.getId();
        UUID merchantId = merchant.getId();

        // 4. Ejecución concurrente mediante CyclicBarrier
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger optimisticLockConflicts = new AtomicInteger(0);

        Callable<Void> task1 = () -> {
            try {
                barrier.await(5, TimeUnit.SECONDS);
                caseService.resolveCase(merchantId, caseId, analyst1.getId(), ResolveCaseRequest.builder()
                        .resolution(CaseResolution.APPROVED)
                        .comment("Aprobado por Analista 1 tras verificación telefónica")
                        .build());
                successes.incrementAndGet();
            } catch (org.springframework.dao.OptimisticLockingFailureException |
                     org.hibernate.StaleObjectStateException |
                     jakarta.persistence.OptimisticLockException |
                     com.pillapago.evaluation.exception.CaseAlreadyResolvedException ex) {
                optimisticLockConflicts.incrementAndGet();
            } catch (Exception e) {
                if (e.getCause() instanceof org.hibernate.StaleObjectStateException ||
                    e.getCause() instanceof jakarta.persistence.OptimisticLockException) {
                    optimisticLockConflicts.incrementAndGet();
                }
            }
            return null;
        };

        Callable<Void> task2 = () -> {
            try {
                barrier.await(5, TimeUnit.SECONDS);
                caseService.resolveCase(merchantId, caseId, analyst2.getId(), ResolveCaseRequest.builder()
                        .resolution(CaseResolution.REJECTED)
                        .comment("Rechazado por Analista 2 por IP proxy anónima")
                        .build());
                successes.incrementAndGet();
            } catch (org.springframework.dao.OptimisticLockingFailureException |
                     org.hibernate.StaleObjectStateException |
                     jakarta.persistence.OptimisticLockException |
                     com.pillapago.evaluation.exception.CaseAlreadyResolvedException ex) {
                optimisticLockConflicts.incrementAndGet();
            } catch (Exception e) {
                if (e.getCause() instanceof org.hibernate.StaleObjectStateException ||
                    e.getCause() instanceof jakarta.persistence.OptimisticLockException) {
                    optimisticLockConflicts.incrementAndGet();
                }
            }
            return null;
        };

        Future<Void> f1 = executor.submit(task1);
        Future<Void> f2 = executor.submit(task2);

        f1.get(10, TimeUnit.SECONDS);
        f2.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        // 5. Validaciones de consistencia
        assertThat(successes.get()).isEqualTo(1);
        assertThat(optimisticLockConflicts.get()).isEqualTo(1);

        CaseEntity finalCase = caseRepository.findById(caseId).orElseThrow();
        assertThat(finalCase.getStatus()).isEqualTo(CaseStatus.RESOLVED);
        assertThat(finalCase.getVersion()).isGreaterThan(0);
        assertThat(finalCase.getResolution()).isNotNull();
        assertThat(finalCase.getResolvedAt()).isNotNull();
    }
}
