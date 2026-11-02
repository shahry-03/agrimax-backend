package com.agrimax.identity.repository;

import com.agrimax.identity.entity.BackupCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BackupCodeRepository extends JpaRepository<BackupCode, UUID> {

    List<BackupCode> findByUserIdAndUsedFalse(UUID userId);

    @Modifying
    @Query("DELETE FROM BackupCode bc WHERE bc.user.id = :userId")
    void deleteByUserId(@Param("userId") UUID userId);

    long countByUserIdAndUsedFalse(UUID userId);
}