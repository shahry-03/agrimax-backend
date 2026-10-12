package com.agrimax.identity.handler;

import com.agrimax.common.voice.CommandHandler;
import com.agrimax.common.voice.CommandResult;
import com.agrimax.identity.dto.SendOtpRequest;
import com.agrimax.identity.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Handles LOGIN voice intent — sends OTP to the farmer's phone number.
 */
@Component
@RequiredArgsConstructor
public class LoginCommandHandler implements CommandHandler {

    private final AuthService authService;

    @Override
    public String supportedIntent() {
        return "LOGIN";
    }

    @Override
    public CommandResult handle(Map<String, Object> parameters, UUID userId) {
        String phone = (String) parameters.get("phone");
        if (phone == null || phone.isBlank()) {
            return CommandResult.fail(
                    "Please tell me your phone number",
                    "اپنا فون نمبر بتائیں"
            );
        }

        try {
            authService.sendOtp(new SendOtpRequest(phone));
            return CommandResult.ok(
                    "OTP has been sent to " + phone,
                    "OTP بھیج دیا گیا ہے " + phone + " پر"
            );
        } catch (Exception e) {
            return CommandResult.fail(
                    "Could not send OTP. Please try again.",
                    "OTP نہیں بھیجا جا سکا۔ دوبارہ کوشش کریں۔"
            );
        }
    }
}
