package com.agrimax.identity.handler;

import com.agrimax.common.session.ConversationSession;
import com.agrimax.common.session.ConversationSessionManager;
import com.agrimax.common.voice.CommandHandler;
import com.agrimax.common.voice.CommandResult;
import com.agrimax.identity.dto.AuthResponse;
import com.agrimax.identity.dto.VerifyOtpRequest;
import com.agrimax.identity.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class VerifyOtpCommandHandler implements CommandHandler {

    private final AuthService authService;
    private final ConversationSessionManager sessionManager;

    @Override
    public String supportedIntent() {
        return "VERIFY_OTP";
    }

    @Override
    public CommandResult handle(Map<String, Object> parameters, UUID userId) {
        String otp = (String) parameters.get("otp");
        String sessionId = (String) parameters.get("sessionId");

        if (otp == null || otp.isBlank()) {
            return CommandResult.fail(
                    "Please tell me the OTP you received.",
                    "براہ کرم موصولہ OTP بتائیں۔"
            );
        }

        // Session se phone number nikaalo
        ConversationSession session = sessionManager.getOrCreate(sessionId);
        String phone = (String) session.getContext().get("phoneNumber");

        if (phone == null) {
            return CommandResult.fail(
                    "Session expired. Please tell me your phone number again.",
                    "سیشن ختم ہو گیا۔ براہ کرم دوبارہ اپنا فون نمبر بتائیں۔"
            );
        }

        try {
            // AuthService se OTP verify karwao
            AuthResponse response = authService.verifyOtp(new VerifyOtpRequest(phone, otp));
            log.info("Voice login successful for phone: {}", phone);

            // Session clear kar do taaki phone number na rahe
            sessionManager.clearSession(sessionId);

            // Frontend ko token wapas bhejo
            return CommandResult.ok(
                    "Login successful! Welcome to AgriMax.",
                    "لاگ ان کامیاب! ایگری میکس میں خوش آمدید۔",
                    Map.of(
                            "accessToken", response.accessToken(),
                            "refreshToken", response.refreshToken(),
                            "userId", response.userId(),
                            "name", response.name() != null ? response.name() : "Farmer"
                    )
            );
        } catch (Exception e) {
            log.warn("OTP verification failed for phone {}: {}", phone, e.getMessage());
            return CommandResult.fail(
                    "Invalid or expired OTP. Please try again.",
                    "غلط یا ختم شدہ OTP۔ دوبارہ کوشش کریں۔"
            );
        }
    }
}