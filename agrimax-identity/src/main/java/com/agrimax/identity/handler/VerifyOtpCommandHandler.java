package com.agrimax.identity.handler;

import com.agrimax.common.session.ConversationSession;
import com.agrimax.common.session.ConversationSessionManager;
import com.agrimax.common.voice.CommandHandler;
import com.agrimax.common.voice.CommandResult;
import com.agrimax.common.voice.UIAction;
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
            return CommandResult.failWithAction(
                    "Please tell me the OTP you received.",
                    "براہ کرم موصولہ OTP بتائیں۔",
                    UIAction.navigate("/login", Map.of("step", "otp"))
            );
        }

        ConversationSession session = sessionManager.getOrCreate(sessionId);
        String phone = (String) session.getContext().get("phoneNumber");

        if (phone == null) {
            return CommandResult.failWithAction(
                    "Session expired. Please tell me your phone number again.",
                    "سیشن ختم ہو گیا۔ براہ کرم دوبارہ اپنا فون نمبر بتائیں۔",
                    UIAction.navigate("/login", Map.of("step", "phone"))
            );
        }

        try {
            AuthResponse response = authService.verifyOtp(new VerifyOtpRequest(phone, otp));
            log.info("Voice login successful for phone: {}", phone);

            sessionManager.clearSession(sessionId);

            return CommandResult.okWithAction(
                    "Login successful! Welcome to AgriMax.",
                    "لاگ ان کامیاب! ایگری میکس میں خوش آمدید۔",
                    UIAction.navigate("/", Map.of(
                            "loginSuccess", true,
                            "userId", response.userId().toString()
                    )),
                    Map.of(
                            "accessToken", response.accessToken(),
                            "refreshToken", response.refreshToken(),
                            "userId", response.userId(),
                            "name", response.name() != null ? response.name() : "Farmer"
                    )
            );
        } catch (Exception e) {
            log.warn("OTP verification failed for phone {}: {}", phone, e.getMessage());
            return CommandResult.failWithAction(
                    "Invalid or expired OTP. Please try again.",
                    "غلط یا ختم شدہ OTP۔ دوبارہ کوشش کریں۔",
                    UIAction.navigate("/login", Map.of("step", "otp"))
            );
        }
    }
}