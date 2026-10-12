package com.agrimax.identity.handler;

import com.agrimax.common.session.ConversationSession;
import com.agrimax.common.session.ConversationSessionManager;
import com.agrimax.common.voice.CommandHandler;
import com.agrimax.common.voice.CommandResult;
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
    private final ConversationSessionManager sessionManager; // Naya dependency

    @Override
    public String supportedIntent() {
        return "LOGIN";
    }

    @Override
    public CommandResult handle(Map<String, Object> parameters, UUID userId) {
        String phone = (String) parameters.get("phone");
        String sessionId = (String) parameters.get("sessionId"); // Frontend se aayega

        if (phone == null || phone.isBlank()) {
            return CommandResult.fail(
                    "Please tell me your phone number",
                    "اپنا فون نمبر بتائیں"
            );
        }

        try {
            authService.sendOtp(new SendOtpRequest(phone));
            
            // Phone number ko session context mein save karo taaki OTP verify karte waqt kaam aaye
            ConversationSession session = sessionManager.getOrCreate(sessionId);
            session.getContext().put("phoneNumber", phone);

            return CommandResult.ok(
                    "OTP has been sent to " + phone + ". Please tell me the OTP.",
                    "OTP بھیج دیا گیا ہے " + phone + " پر۔ براہ کرم OTP بتائیں۔"
            );
        } catch (Exception e) {
            return CommandResult.fail(
                    "Could not send OTP. Please try again.",
                    "OTP نہیں بھیجا جا سکا۔ دوبارہ کوشش کریں۔"
            );
        }
    }
}