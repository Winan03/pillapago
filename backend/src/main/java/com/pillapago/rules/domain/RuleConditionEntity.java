package com.pillapago.rules.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entidad JPA que representa una condición individual de una regla.
 * Las condiciones de una misma regla se evalúan conjuntamente con lógica AND.
 */
@Entity
@Table(name = "rule_conditions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RuleConditionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private RuleEntity rule;

    @Column(name = "field_name", nullable = false, length = 100)
    private String fieldName;

    @Column(nullable = false, length = 30)
    private String operator;

    @Column(name = "target_value", nullable = false, columnDefinition = "TEXT")
    private String targetValue;

    @Column(name = "value_type", nullable = false, length = 30)
    private String valueType;

    @Column(name = "order_index", nullable = false)
    @Builder.Default
    private Integer orderIndex = 0;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (orderIndex == null) {
            orderIndex = 0;
        }
    }
}
