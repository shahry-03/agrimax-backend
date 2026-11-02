package com.agrimax.identity.repository;

import com.agrimax.identity.entity.OtpToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OtpTokenRepository extends JpaRepository<OtpToken, UUID> {

    Optional<OtpToken> findTopByUser_PhoneNumberAndVerifiedFalseOrderByCreatedAtDesc(String phoneNumber);

    // 🆕 — for detecting first-ever successful verification
    boolean existsByUser_IdAndVerifiedTrue(UUID userId);

    // 🆕 — for rate limiting OTP sends
    @Query("""
        SELECT COUNT(o) FROM OtpToken o
        WHERE o.user.phoneNumber = :phone
          AND o.createdAt > :since
        """)
    long countRecentByPhone(@Param("phone") String phone, @Param("since") Instant since);
}