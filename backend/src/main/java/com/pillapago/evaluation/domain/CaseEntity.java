package com.pillapago.evaluation.domain;

import com.pillapago.tenant.domain.Merchant;
import com.pillapago.tenant.domain.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entidad JPA que representa un caso generado para analistas de fraude (HU-09).
 * Se crea automáticamente de forma asíncrona cuando una transacción resulta en REVIEW.
 */
@Entity
@Table(name = "cases")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Merchant merchant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private TransactionEntity transaction;

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String status = "PENDING_REVIEW";

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String priority = "MEDIUM";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private User assignedTo;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (updatedAt == null) updatedAt = OffsetDateTime.now();
        if (status == null) status = "PENDING_REVIEW";
        if (priority == null) priority = "MEDIUM";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
