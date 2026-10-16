package com.pillapago.tenant.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entidad que representa a un comercio (tenant) en el sistema.
 */
@Entity
@Table(name = "merchants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Merchant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String uuid;

    @Column(name = "review_threshold")
    private Integer reviewThreshold;

    @Column(name = "reject_threshold")
    private Integer rejectThreshold;

    @Column(name = "fail_policy")
    private String failPolicy;

    @Column(name = "data_retention_months")
    private Integer dataRetentionMonths;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (updatedAt == null) updatedAt = OffsetDateTime.now();
        if (reviewThreshold == null) reviewThreshold = 50;
        if (rejectThreshold == null) rejectThreshold = 80;
        if (failPolicy == null) failPolicy = "FAIL_CLOSED";
        if (dataRetentionMonths == null) dataRetentionMonths = 13;
    }
}
