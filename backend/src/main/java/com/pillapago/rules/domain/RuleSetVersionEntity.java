package com.pillapago.rules.domain;

import com.pillapago.tenant.domain.Merchant;
import com.pillapago.tenant.domain.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entidad JPA que representa una versión inmutable publicada de un conjunto de reglas (HU-08).
 * Almacena el snapshot exacto de reglas compiladas para garantizar reproducibilidad forense y rollback instantáneo.
 */
@Entity
@Table(name = "rule_set_versions", uniqueConstraints = {
        @UniqueConstraint(name = "idx_rsv_merchant_version_name", columnNames = {"merchant_id", "version_name"}),
        @UniqueConstraint(name = "idx_rsv_merchant_version_number", columnNames = {"merchant_id", "version_number"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RuleSetVersionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Merchant merchant;

    @Column(name = "version_name", nullable = false, length = 50)
    private String versionName; // ej: "v1", "v2"

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber; // ej: 1, 2

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private RuleSetVersionStatus status = RuleSetVersionStatus.ACTIVE;

    @Column(name = "rules_snapshot", nullable = false, columnDefinition = "TEXT")
    private String rulesSnapshot; // Snapshot JSON serializado

    @Column(name = "rules_count", nullable = false)
    @Builder.Default
    private Integer rulesCount = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private User createdBy;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (status == null) status = RuleSetVersionStatus.ACTIVE;
        if (rulesCount == null) rulesCount = 0;
    }
}
