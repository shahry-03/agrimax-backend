package com.agrimax.identity.handler;

import com.agrimax.common.session.ConversationSession;
import com.agrimax.common.session.ConversationSessionManager;
import com.agrimax.common.voice.CommandHandler;
import com.agrimax.common.voice.CommandResult;
import com.agrimax.common.voice.UIAction;
import com.agrimax.identity.dto.SendOtpRequest;
import com.agrimax.identity.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class LoginCommandHandler implements CommandHandler {

    private final AuthService authService;
    private final ConversationSessionManager sessionManager;

    @Override
    public String supportedIntent() {
        return "LOGIN";
    }

    @Override
    public CommandResult handle(Map<String, Object> parameters, UUID userId) {
        String phone = (String) parameters.get("phone");
        String sessionId = (String) parameters.get("sessionId");

        // No phone yet → open login page and ask
        if (phone == null || phone.isBlank()) {
            return CommandResult.failWithAction(
                    "Please tell me your phone number",
                    "اپنا فون نمبر بتائیں",
                    UIAction.navigate("/login", Map.of("step", "phone"))
            );
        }

        try {
            authService.sendOtp(new SendOtpRequest(phone));

            ConversationSession session = sessionManager.getOrCreate(sessionId);
            session.getContext().put("phoneNumber", phone);
            session.getContext().put("awaitingOtp", true);

            return CommandResult.okWithAction(
                    "OTP has been sent to " + phone + ". Please tell me the OTP.",
                    "OTP بھیج دیا گیا ہے " + phone + " پر۔ براہ کرم OTP بتائیں۔",
                    UIAction.navigate("/login", Map.of("step", "otp", "phone", phone))
            );
        } catch (Exception e) {
            return CommandResult.failWithAction(
                    "Could not send OTP. Please try again.",
                    "OTP نہیں بھیجا جا سکا۔ دوبارہ کوشش کریں۔",
                    UIAction.navigate("/login", Map.of("step", "phone"))
            );
        }
    }
}