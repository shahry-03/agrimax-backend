package com.agrimax.identity.service.impl;

import com.agrimax.identity.config.IdentityProperties;
import com.agrimax.identity.service.EmailService;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final IdentityProperties properties;

    // ═══════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════

    @Override
    @Async
    public void sendVerificationEmail(String toEmail, String userName, String token) {
        String link = baseUrl() + "/verify-email?token=" + token;
        String body = renderEmail(
                "Verify your email",
                "<p>Hi <strong>" + esc(userName) + "</strong>,</p>" +
                "<p>Welcome to AgriMax! Please verify your email to activate your account.</p>" +
                "<p>This link expires in <strong>24 hours</strong>.</p>",
                "Verify Email", link);
        sendHtml(toEmail, "Verify your AgriMax email", body);
    }

    @Override
    @Async
    public void sendPasswordResetEmail(String toEmail, String userName, String token) {
        String link = baseUrl() + "/reset-password?token=" + token;
        String body = renderEmail(
                "Reset your password",
                "<p>Hi <strong>" + esc(userName) + "</strong>,</p>" +
                "<p>We received a request to reset your AgriMax password.</p>" +
                "<p>This link expires in <strong>30 minutes</strong>. If you didn't request this, you can ignore this email.</p>",
                "Reset Password", link);
        sendHtml(toEmail, "Reset your AgriMax password", body);
    }

    @Override
    @Async
    public void sendWelcomeEmail(String toEmail, String userName) {
        String body = renderEmail(
                "Welcome to AgriMax!",
                "<p>Hi <strong>" + esc(userName) + "</strong>,</p>" +
                "<p>Your email is verified. Welcome to AgriMax — the AI-powered farming platform.</p>" +
                "<p>You can now buy and sell crops, get AI recommendations, and manage your wallet.</p>",
                null, null);
        sendHtml(toEmail, "Welcome to AgriMax!", body);
    }

    @Override
    @Async
    public void sendPasswordChangedEmail(String toEmail, String userName) {
        String body = renderEmail(
                "Password changed",
                "<p>Hi <strong>" + esc(userName) + "</strong>,</p>" +
                "<p>Your AgriMax password was successfully changed.</p>" +
                "<p>If this wasn't you, please reset your password immediately and contact support.</p>",
                null, null);
        sendHtml(toEmail, "Your AgriMax password was changed", body);
    }

    @Override
    @Async
    public void sendAccountLockedEmail(String toEmail, String userName,
                                       int maxAttempts, int lockDurationMinutes) {
        String body = renderEmail(
                "Your account has been locked",
                "<p>Hi <strong>" + esc(userName) + "</strong>,</p>" +
                "<p>We noticed " + maxAttempts + " failed login attempts on your account. " +
                "For your security, we've temporarily locked it.</p>" +
                "<p>Your account will be automatically unlocked after <strong>" +
                lockDurationMinutes + " minutes</strong>, or you can reset your password.</p>",
                "Reset Password", baseUrl() + "/forgot-password");
        sendHtml(toEmail, "Security alert: Your AgriMax account is locked", body);
    }

    @Override
    @Async
    public void sendAccountUnlockedEmail(String toEmail, String userName) {
        String body = renderEmail(
                "Your account has been unlocked",
                "<p>Hi <strong>" + esc(userName) + "</strong>,</p>" +
                "<p>Your AgriMax account is active again. You can log in now.</p>",
                null, null);
        sendHtml(toEmail, "Your AgriMax account is unlocked", body);
    }

    // ═══════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════

    private String baseUrl() {
        String url = properties.getEmail().getFrontendBaseUrl();
        return url != null ? url : "http://localhost:3000";
    }

    private void sendHtml(String to, String subject, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(properties.getEmail().getFrom(), properties.getEmail().getFromName());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
            log.info("Email sent → {} | subject: {}", to, subject);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
            // Don't rethrow — email failure shouldn't break the flow
        }
    }

    private String renderEmail(String title, String bodyHtml, String buttonText, String buttonLink) {
        String buttonHtml = "";
        if (buttonText != null && buttonLink != null) {
            buttonHtml = "<p style='text-align:center;margin:30px 0'>" +
                    "<a href='" + esc(buttonLink) + "' style='background:#2e7d32;color:#fff;" +
                    "text-decoration:none;padding:12px 28px;border-radius:6px;display:inline-block;" +
                    "font-weight:600'>" + esc(buttonText) + "</a></p>";
        }

        return "<!DOCTYPE html><html><body style='font-family:Arial,sans-serif;background:#f5f5f5;" +
                "padding:20px;margin:0;color:#333'>" +
                "<div style='max-width:600px;margin:0 auto;background:#fff;padding:32px;" +
                "border-radius:8px;box-shadow:0 2px 8px rgba(0,0,0,0.05)'>" +
                "<h1 style='color:#2e7d32;font-size:22px;margin-top:0'>" + esc(title) + "</h1>" +
                bodyHtml + buttonHtml +
                "<hr style='border:none;border-top:1px solid #eee;margin:32px 0 16px'>" +
                "<p style='color:#999;font-size:12px;margin:0'>" +
                "AgriMax — AI-powered farming platform for Pakistan</p>" +
                "</div></body></html>";
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}