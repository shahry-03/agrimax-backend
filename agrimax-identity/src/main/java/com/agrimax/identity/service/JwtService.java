package com.agrimax.identity.service;

import com.agrimax.identity.config.JwtProperties;
import com.agrimax.identity.entity.Role;
import com.agrimax.identity.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * JWT service — backward-compatible upgrade.
 *
 * <p><b>Old methods preserved</b> (used by existing farmer OTP flow):
 *   {@link #generateAccessToken(UUID, String)}, {@link #generateRefreshToken(UUID, String)},
 *   {@link #isTokenValid(String)}, {@link #extractUserId(String)}, {@link #extractRole(String)}</p>
 *
 * <p><b>New methods</b> (reference-style, used by buyer/admin + sessions):
 *   {@link #generateAccessToken(User, String)}, {@link #generateRefreshToken(User, String)},
 *   {@link #generateTwoFactorTempToken(User)}, {@link #isAccessToken}, etc.</p>
 */
@Slf4j
@Service
@Getter
public class JwtService {

    private final SecretKey signingKey;
    private final long accessExpiryMs;
    private final long refreshExpiryMs;
    private final String issuer;

    public JwtService(JwtProperties props) {
        if (props.getSecret() == null || props.getSecret().isBlank()) {
            throw new IllegalStateException("agrimax.jwt.secret is not configured");
        }
        this.signingKey = resolveKey(props.getSecret());
        this.accessExpiryMs = props.getAccessTokenExpiryMs();
        this.refreshExpiryMs = props.getRefreshTokenExpiryMs();
        this.issuer = props.getIssuer();
    }

    /** Try base64 first, fallback to raw bytes (both modes supported). */
    private SecretKey resolveKey(String secret) {
        try {
            byte[] decoded = Decoders.BASE64.decode(secret);
            if (decoded.length >= 32) {
                return Keys.hmacShaKeyFor(decoded);
            }
        } catch (Exception ignored) { /* not base64 — try raw */ }
        byte[] raw = secret.getBytes(StandardCharsets.UTF_8);
        if (raw.length < 32) {
            throw new IllegalStateException(
                "agrimax.jwt.secret must be ≥ 32 bytes after decoding");
        }
        return Keys.hmacShaKeyFor(raw);
    }

    // ═══════════════════════════════════════════════════════════
    //  LEGACY (backward compat — do NOT change signatures)
    // ═══════════════════════════════════════════════════════════

    public String generateAccessToken(UUID userId, String role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(userId.toString())
                .issuer(issuer)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessExpiryMs))
                .claim("role", role)
                .claim("type", "access")
                .signWith(signingKey)
                .compact();
    }

    public String generateRefreshToken(UUID userId, String role) {
        Date now = new Date();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId.toString())
                .issuer(issuer)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + refreshExpiryMs))
                .claim("role", role)
                .claim("type", "refresh")
                .signWith(signingKey)
                .compact();
    }

    public boolean isTokenValid(String token) {
        try {
            Claims claims = parseToken(token).getPayload();
            return !claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    public UUID extractUserId(String token) {
        return UUID.fromString(parseToken(token).getPayload().getSubject());
    }

    public String extractRole(String token) {
        return parseToken(token).getPayload().get("role", String.class);
    }

    // ═══════════════════════════════════════════════════════════
    //  NEW (reference-style — for buyer/admin + sessions)
    // ═══════════════════════════════════════════════════════════

    public String generateAccessToken(User user, String refreshJti) {
        Instant now = Instant.now();
        List<String> roles = user.getRoles() == null ? List.of()
                : user.getRoles().stream().map(Role::getRoleName).toList();

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.getId().toString())
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(accessExpiryMs)))
                .claim("email", user.getEmail())
                .claim("role", user.getRole())                    // legacy singular
                .claim("roles", roles)                             // reference plural
                .claim("type", "access")
                .claim("refreshJti", refreshJti != null ? refreshJti : "")
                .signWith(signingKey)
                .compact();
    }

    public String generateRefreshToken(User user, String jti) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(jti)
                .subject(user.getId().toString())
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(refreshExpiryMs)))
                .claim("type", "refresh")
                .signWith(signingKey)
                .compact();
    }

    /** Short-lived (5 min) token for 2FA step-2. */
    public String generateTwoFactorTempToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.getId().toString())
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(300)))
                .claim("type", "2fa_temp")
                .signWith(signingKey)
                .compact();
    }

    // ═══════════════════════════════════════════════════════════
    //  PARSING / CLAIM EXTRACTION
    // ═══════════════════════════════════════════════════════════

    public Jws<Claims> parseToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token);
    }

    public boolean isAccessToken(String token) {
        try {
            return "access".equals(parseToken(token).getPayload().get("type"));
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isRefreshToken(String token) {
        try {
            return "refresh".equals(parseToken(token).getPayload().get("type"));
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isTwoFactorTempToken(String token) {
        try {
            return "2fa_temp".equals(parseToken(token).getPayload().get("type"));
        } catch (Exception e) {
            return false;
        }
    }

    public UUID getUserId(String token) {
        return UUID.fromString(parseToken(token).getPayload().getSubject());
    }

    public String getJtiFromToken(String token) {
        return parseToken(token).getPayload().getId();
    }

    public String getEmailFromToken(String token) {
        return parseToken(token).getPayload().get("email", String.class);
    }

    @SuppressWarnings("unchecked")
    public List<String> getRolesFromToken(String token) {
        return parseToken(token).getPayload().get("roles", List.class);
    }

    public String getRefreshJtiFromToken(String token) {
        try {
            String jti = parseToken(token).getPayload().get("refreshJti", String.class);
            return (jti == null || jti.isBlank()) ? null : jti;
        } catch (Exception e) {
            return null;
        }
    }

    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}