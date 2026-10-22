package com.pillapago.auth.repository;

import com.pillapago.auth.domain.EmailVerificationCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailVerificationCodeRepository extends JpaRepository<EmailVerificationCode, UUID> {

    Optional<EmailVerificationCode> findTopByUserIdAndUsedFalseOrderByCreatedAtDesc(UUID userId);

    List<EmailVerificationCode> findAllByUserIdAndUsedFalse(UUID userId);

    @Modifying
    @Query("UPDATE EmailVerificationCode c SET c.used = true WHERE c.user.id = :userId AND c.used = false")
    void invalidatePendingCodes(@Param("userId") UUID userId);

    void deleteAllByUserId(UUID userId);
}
