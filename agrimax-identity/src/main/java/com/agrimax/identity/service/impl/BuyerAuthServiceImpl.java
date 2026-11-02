package com.agrimax.identity.service.impl;

import com.agrimax.common.event.UserRegisteredEvent;
import com.agrimax.common.exception.BadRequestException;
import com.agrimax.common.exception.EmailNotVerifiedException;
import com.agrimax.identity.dto.*;
import com.agrimax.identity.entity.*;
import com.agrimax.identity.repository.*;
import com.agrimax.identity.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class BuyerAuthServiceImpl implements BuyerAuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final VerificationTokenService verificationTokenService;
    private final EmailService emailService;
    private final AccountLockoutService accountLockoutService;
    private final ApplicationEventPublisher eventPublisher;

    // ═══════════════════════════════════════════════════════════
    //  REGISTER
    // ═══════════════════════════════════════════════════════════

    @Override
    @Transactional
    public UserResponse register(BuyerRegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BadRequestException("Email already registered");
        }

        Role buyerRole = roleRepository.findByRoleName("BUYER")
                .orElseThrow(() -> new IllegalStateException("BUYER role missing — check V3 seed"));

        User user = User.builder()
                .email(request.email())
                .name(request.name())
                .password(passwordEncoder.encode(request.password()))
                .role("BUYER")
                .preferredMode(UxMode.TEXT)
                .emailVerified(false)
                .enabled(true)
                .roles(new HashSet<>(Set.of(buyerRole)))
                .build();

        user = userRepository.save(user);

        VerificationToken vt = verificationTokenService.createEmailVerificationToken(user);
        emailService.sendVerificationEmail(user.getEmail(), user.getName(), vt.getToken());

        log.info("Buyer registered: {} (email verification sent)", user.getEmail());
        return UserResponse.from(user);
    }

    // ═══════════════════════════════════════════════════════════
    //  LOGIN
    // ═══════════════════════════════════════════════════════════

    @Override
    @Transactional
    public TokenResponse login(BuyerLoginRequest request, String ipAddress, String userAgent) {
        // 1. Load user (don't leak existence)
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        // 2. Lockout check
        accountLockoutService.checkAccountLocked(user);

        // 3. Password check via AuthenticationManager
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        } catch (BadCredentialsException e) {
            boolean justLocked = accountLockoutService.recordFailedAttempt(user);
            log.warn("Login failed for email: {} (locked now: {})", request.email(), justLocked);
            throw new BadCredentialsException("Invalid email or password");
        }

        // 4. Reset counter on success
        accountLockoutService.resetFailedAttempts(user);

        // 5. Email verification check
        if (!user.isEmailVerified()) {
            throw new EmailNotVerifiedException(
                    "Please verify your email. Check your inbox for the verification link.");
        }

        // 6. Account enabled check
        if (!user.isEnabled()) {
            throw new DisabledException("Account is disabled");
        }

        // 7. 2FA check (B6 will complete verify flow — for now just flag)
        if (user.isTwoFactorEnabled()) {
            String tempToken = jwtService.generateTwoFactorTempToken(user);
            log.info("2FA required for email: {}", request.email());
            return TokenResponse.requiresTwoFactor(tempToken);
        }

        // 8. Create DB-backed refresh token + issue JWTs
        RefreshToken rt = refreshTokenService.createForUser(user, ipAddress, userAgent);
        String accessToken = jwtService.generateAccessToken(user, rt.getJti());
        String refreshToken = jwtService.generateRefreshToken(user, rt.getJti());

        log.info("Buyer login successful: {} (role={})", user.getEmail(), user.getRole());
        return TokenResponse.of(accessToken, refreshToken, jwtService.getAccessExpiryMs(), user);
    }

    // ═══════════════════════════════════════════════════════════
    //  EMAIL VERIFICATION
    // ═══════════════════════════════════════════════════════════

    @Override
    @Transactional
    public void verifyEmail(String tokenValue) {
        VerificationToken token = verificationTokenService.validateToken(
                tokenValue, VerificationToken.TokenType.EMAIL_VERIFICATION);

        User user = token.getUser();
        boolean firstVerification = !user.isEmailVerified();

        user.setEmailVerified(true);
        userRepository.saveAndFlush(user);
        verificationTokenService.markUsed(token);

        // Publish registration event → creates wallet (via WalletEventListener)
        if (firstVerification) {
            log.info("First email verification: {} — publishing UserRegisteredEvent", user.getId());
            eventPublisher.publishEvent(
                    new UserRegisteredEvent(user.getId(), user.getPhoneNumber(), user.getRole()));
        }

        // Send welcome email
        emailService.sendWelcomeEmail(user.getEmail(), user.getName());
    }

    @Override
    @Transactional
    public void resendVerificationEmail(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            if (user.isEmailVerified()) return;
            VerificationToken vt = verificationTokenService.createEmailVerificationToken(user);
            emailService.sendVerificationEmail(user.getEmail(), user.getName(), vt.getToken());
        });
        // Silently succeed either way (no info leak)
    }

    // ═══════════════════════════════════════════════════════════
    //  PASSWORD RESET
    // ═══════════════════════════════════════════════════════════

    @Override
    @Transactional
    public void forgotPassword(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            if (user.getPassword() == null) return;  // OAuth-only user
            VerificationToken token = verificationTokenService.createPasswordResetToken(user);
            emailService.sendPasswordResetEmail(user.getEmail(), user.getName(), token.getToken());
            log.info("Password reset email sent for: {}", email);
        });
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        VerificationToken token = verificationTokenService.validateToken(
                request.token(), VerificationToken.TokenType.PASSWORD_RESET);

        User user = token.getUser();
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.saveAndFlush(user);
        verificationTokenService.markUsed(token);

        // Security: reset lockout counter, revoke all sessions
        accountLockoutService.resetFailedAttempts(user);
        refreshTokenService.revokeAllForUser(user.getId());

        emailService.sendPasswordChangedEmail(user.getEmail(), user.getName());
        log.info("Password reset successful for: {}", user.getEmail());
    }
}