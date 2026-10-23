package com.pillapago.evaluation.domain;

import com.pillapago.evaluation.engine.Decision;
import com.pillapago.tenant.domain.Merchant;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Entidad JPA que representa una transacción evaluada y persistida fuera del Hot Path (HU-06c, HU-08).
 */
@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Merchant merchant;

    @Column(name = "external_transaction_id", nullable = false)
    private String externalTransactionId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 10)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Decision decision;

    @Column(name = "risk_score", nullable = false)
    private Integer riskScore;

    @Column(name = "rule_set_version", length = 50)
    private String ruleSetVersion;

    @Column(nullable = false)
    @Builder.Default
    private Boolean degraded = false;

    @Column(name = "latency_ms", nullable = false)
    private Long latencyMs;

    @Column(name = "customer_id_encrypted")
    private byte[] customerIdEncrypted;

    @Column(name = "customer_email_encrypted")
    private byte[] customerEmailEncrypted;

    @Column(name = "ip_address_encrypted")
    private byte[] ipAddressEncrypted;

    @Column(name = "device_id_encrypted")
    private byte[] deviceIdEncrypted;

    @Column(name = "card_fingerprint_encrypted")
    private byte[] cardFingerprintEncrypted;

    @Column(name = "card_bin", length = 10)
    private String cardBin;

    @Column(name = "card_last_four", length = 10)
    private String cardLastFour;

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    @Column(columnDefinition = "TEXT")
    private String metadata;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<TransactionEvaluationEntity> evaluations = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
