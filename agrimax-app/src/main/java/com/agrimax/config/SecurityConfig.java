package com.agrimax.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final CorsConfigurationSource corsConfigurationSource;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // ── Public endpoints ──
                        .requestMatchers("/api/v1/auth/**").permitAll()          // farmer + buyer auth
                        .requestMatchers("/api/v1/voice/**").permitAll()         // voice pipeline (role check inside)
                        .requestMatchers("/ws/**").permitAll()                    // websocket
                        .requestMatchers("/api/v1/crops/public/**").permitAll()
                        .requestMatchers("/api/v1/ai/tip").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/uploads/**").permitAll()

                        // ── Role-guarded endpoints ──
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/farmer/**").hasRole("FARMER")
                        .requestMatchers("/api/v1/buyer/**").hasRole("BUYER")

                        // ── Everything else: any authenticated user ──
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) -> {
                            res.setStatus(401);
                            res.setContentType("application/json");
                            String msg = (String) req.getAttribute("error");
                            if (msg == null) msg = "Authentication required";
                            res.getWriter().write(json401(msg));
                        })
                        .accessDeniedHandler((req, res, e) -> {
                            res.setStatus(403);
                            res.setContentType("application/json");
                            res.getWriter().write(json403());
                        })
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }

    // ── JSON body helpers (matching ApiResponse shape) ──

    private String json401(String message) {
        return "{\"success\":false,\"error\":\"UNAUTHORIZED\","
                + "\"messageEn\":\"" + safe(message) + "\","
                + "\"messageUr\":\"غیر مجاز رسائی\"}";
    }

    private String json403() {
        return "{\"success\":false,\"error\":\"FORBIDDEN\","
                + "\"messageEn\":\"You don't have permission for this action\","
                + "\"messageUr\":\"آپ کو اس کام کی اجازت نہیں ہے\"}";
    }

    private String safe(String s) {
        return s == null ? "" : s.replace("\"", "'");
    }
}