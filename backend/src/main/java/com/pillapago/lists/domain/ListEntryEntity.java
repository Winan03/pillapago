package com.pillapago.lists.domain;

import com.pillapago.tenant.domain.Merchant;
import com.pillapago.tenant.domain.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entidad JPA que representa una entrada en las listas de bloqueo o de confianza (HU-13 / RF-09).
 * Incluye índice ciego HMAC-SHA256 para búsquedas exactas en memoria O(1) y cifrado de campo Tink
 * para visualización autorizada por el analista de riesgo.
 */
@Entity
@Table(name = "list_entries", uniqueConstraints = {
        @UniqueConstraint(name = "uq_merchant_list_entry", columnNames = {"merchant_id", "list_type", "entry_type", "hashed_value"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ListEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Merchant merchant;

    @Enumerated(EnumType.STRING)
    @Column(name = "list_type", nullable = false, length = 20)
    private ListType listType;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 30)
    private ListEntryType entryType;

    /**
     * Índice ciego determinista (HMAC-SHA256 con clave del comercio).
     * Permite matching en O(1) sin descifrar el contenido original.
     */
    @Column(name = "hashed_value", nullable = false, length = 64)
    private String hashedValue;

    /**
     * Valor original cifrado mediante Google Tink (AES-256-GCM con AAD asociado a la fila).
     */
    @Column(name = "encrypted_value", nullable = false, columnDefinition = "TEXT")
    private String encryptedValue;

    /**
     * Versión enmascarada para presentación visual segura en UI (RNF-15).
     */
    @Column(name = "value_masked", length = 100)
    private String valueMasked;

    @Column(nullable = false, length = 500)
    private String reason;

    /**
     * Fecha y hora límite de validez. Si es null, la regla es permanente.
     */
    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = OffsetDateTime.now();
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    /**
     * Determina si la entrada está activa (no ha expirado aún).
     */
    public boolean isActive() {
        if (expiresAt == null) {
            return true;
        }
        return expiresAt.isAfter(OffsetDateTime.now());
    }
}
