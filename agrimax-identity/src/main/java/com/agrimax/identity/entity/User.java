package com.agrimax.identity.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "users", schema = "identity")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // ── FARMER path (nullable for buyer/admin) ──
    @Column(name = "phone_number", unique = true, length = 15)
    private String phoneNumber;

    // ── BUYER / ADMIN path (nullable for farmer) ──
    @Column(unique = true, length = 300)
    private String email;

    private String password;

    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private boolean emailVerified = false;

    // ── Common ──
    @Column(length = 100)
    private String name;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String role = "FARMER";              // "FARMER"|"BUYER"|"ADMIN"|"DELIVERY" — kept as String for backward compat

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_mode", length = 10)
    @Builder.Default
    private UxMode preferredMode = UxMode.VOICE;

    @Column(name = "language_pref", length = 5)
    @Builder.Default
    private String languagePref = "ur";

    @Column(name = "is_kyc_verified")
    @Builder.Default
    private Boolean isKycVerified = false;

    @Column(name = "profile_image", length = 1000)
    private String image;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    // ── 2FA ──
    @Column(name = "totp_secret", length = 255)
    private String totpSecret;

    @Column(name = "two_factor_enabled", nullable = false)
    @Builder.Default
    private boolean twoFactorEnabled = false;

    // ── Lockout ──
    @Column(name = "failed_login_attempts", nullable = false)
    @Builder.Default
    private int failedLoginAttempts = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_failed_login")
    private Instant lastFailedLogin;

    // ── Provider (OAuth) ──
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private Provider provider = Provider.LOCAL;

    @Column(name = "provider_id", length = 100)
    private String providerId;

    // ── RBAC (from reference — carries permissions) ──
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            schema = "identity",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    @Builder.Default
    private Set<Role> roles = new HashSet<>();

    // ── Timestamps ──
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    // ═══════════════════════════════════════════════════════════
    // UserDetails contract
    // ═══════════════════════════════════════════════════════════

    /**
     * ⚡ KEY DESIGN: returns UUID so existing controllers
     *    that do `UUID.fromString(principal.getName())` keep working.
     */
    @Override
    public String getUsername() {
        return this.id != null ? this.id.toString() : null;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        Set<GrantedAuthority> authorities = new HashSet<>();
        // Backward-compat: single role string
        if (role != null) authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        // Reference-style: role set + permissions
        if (roles != null) {
            for (Role r : roles) {
                if (r.getRoleName() != null)
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + r.getRoleName()));
                if (r.getPermissions() != null)
                    r.getPermissions().forEach(p ->
                            authorities.add(new SimpleGrantedAuthority(p.getName())));
            }
        }
        return authorities;
    }

    @Override public boolean isAccountNonExpired()     { return true; }
    @Override public boolean isAccountNonLocked()      { return !isLocked(); }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled()               { return this.enabled; }

    // ── Lock helpers ──
    public boolean isLocked()      { return lockedUntil != null && Instant.now().isBefore(lockedUntil); }
    public boolean isLockExpired() { return lockedUntil != null && Instant.now().isAfter(lockedUntil); }
}