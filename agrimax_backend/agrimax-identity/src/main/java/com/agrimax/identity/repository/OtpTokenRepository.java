package com.agrimax.identity.repository;

import com.agrimax.identity.entity.OtpToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OtpTokenRepository extends JpaRepository<OtpToken, UUID> {
    Optional<OtpToken> findTopByUser_PhoneNumberAndVerifiedFalseOrderByCreatedAtDesc(String phoneNumber);
}
