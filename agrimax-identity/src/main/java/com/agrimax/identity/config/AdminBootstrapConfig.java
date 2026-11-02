package com.agrimax.identity.config;

import com.agrimax.identity.entity.Role;
import com.agrimax.identity.entity.UxMode;
import com.agrimax.identity.entity.User;
import com.agrimax.identity.repository.RoleRepository;
import com.agrimax.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * Creates bootstrap admin on startup if enabled and not present.
 * Flyway V3 has already seeded roles/permissions before this runs.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class AdminBootstrapConfig implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final IdentityProperties properties;

    @Override
    public void run(String... args) {
        IdentityProperties.Bootstrap.Admin cfg = properties.getBootstrap().getAdmin();

        if (!cfg.isEnabled()) {
            log.debug("Admin bootstrap disabled — skipping");
            return;
        }
        if (isBlank(cfg.getEmail()) || isBlank(cfg.getPassword())) {
            log.warn("Admin bootstrap enabled but email/password missing — skipping");
            return;
        }
        if (userRepository.existsByEmail(cfg.getEmail())) {
            log.info("Bootstrap admin already exists: {}", cfg.getEmail());
            return;
        }

        Role adminRole = roleRepository.findByRoleName("ADMIN")
                .orElseThrow(() -> new IllegalStateException("ADMIN role missing — check V3 migration"));

        Set<Role> roles = new HashSet<>();
        roles.add(adminRole);

        User admin = User.builder()
                .email(cfg.getEmail())
                .name(cfg.getName())
                .password(passwordEncoder.encode(cfg.getPassword()))
                .role("ADMIN")
                .preferredMode(UxMode.TEXT)
                .emailVerified(true)          // ⚡ no email verification needed
                .enabled(true)
                .roles(roles)
                .build();

        userRepository.save(admin);
        log.info("════════════════════════════════════════════");
        log.info("  BOOTSTRAP ADMIN CREATED");
        log.info("  Email: {}", cfg.getEmail());
        log.info("  Change password immediately after first login!");
        log.info("════════════════════════════════════════════");
    }

    private boolean isBlank(String s) { return s == null || s.isBlank(); }
}