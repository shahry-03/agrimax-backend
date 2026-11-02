package com.agrimax.config;

import com.agrimax.identity.repository.UserRepository;
import com.agrimax.identity.service.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * JWT auth filter — validates Bearer access tokens.
 *
 * <p>Sets <b>principal = User entity</b> (implements UserDetails), so:
 * <ul>
 *   <li>Existing controllers using {@code UUID.fromString(principal.getName())} keep working
 *       (User.getUsername() returns id.toString())</li>
 *   <li>New controllers use {@code @AuthenticationPrincipal User user}</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        // No token → let SecurityConfig decide (public endpoints pass, protected → 401)
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);

        try {
            Jws<Claims> parsed = jwtService.parseToken(token);
            Claims claims = parsed.getPayload();

            // Only ACCESS tokens authenticate. Refresh / 2fa_temp ignored here.
            String type = claims.get("type", String.class);
            if (!"access".equals(type)) {
                filterChain.doFilter(request, response);
                return;
            }

            UUID userId;
            try {
                userId = UUID.fromString(claims.getSubject());
            } catch (IllegalArgumentException e) {
                log.debug("Invalid userId in token subject: {}", claims.getSubject());
                filterChain.doFilter(request, response);
                return;
            }

            // Skip if already authenticated (idempotent)
            if (SecurityContextHolder.getContext().getAuthentication() != null) {
                filterChain.doFilter(request, response);
                return;
            }

            userRepository.findById(userId).ifPresentOrElse(user -> {
                if (!user.isEnabled()) {
                    log.debug("Token belongs to disabled user: {}", userId);
                    return;
                }
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(
                                user, null, user.getAuthorities());
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }, () -> log.debug("Token subject not found in DB: {}", userId));

        } catch (ExpiredJwtException e) {
            log.debug("Access token expired: {}", e.getMessage());
            request.setAttribute("error", "Token Expired");
        } catch (Exception e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            request.setAttribute("error", "Invalid Token");
        }

        filterChain.doFilter(request, response);
    }
}