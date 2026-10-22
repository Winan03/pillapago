package com.pillapago.auth.domain;

import com.pillapago.tenant.domain.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entidad que gestiona los códigos numéricos de verificación de correo (HU-01).
 * Implementa expiración a 15 minutos, límite de intentos y almacenamiento seguro mediante hash SHA-256.
 */
@Entity
@Table(name = "email_verification_codes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailVerificationCode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(nullable = false)
    @Builder.Default
    private Integer attempts = 0;

    @Column(nullable = false)
    @Builder.Default
    private Boolean used = false;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (attempts == null) attempts = 0;
        if (used == null) used = false;
        if (expiresAt == null) expiresAt = OffsetDateTime.now().plusMinutes(15);
    }

    public boolean isExpired() {
        return OffsetDateTime.now().isAfter(expiresAt);
    }

    public boolean hasExceededAttempts(int maxAttempts) {
        return attempts >= maxAttempts;
    }
}
