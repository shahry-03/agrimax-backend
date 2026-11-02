package com.agrimax.identity.repository;

import com.agrimax.identity.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByPhoneNumber(String phoneNumber);
    boolean existsByPhoneNumber(String phoneNumber);

    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    // ── 🆕 Admin dashboard ──
    long countByRole(String role);
    List<User> findTop5ByOrderByCreatedAtDesc();
}