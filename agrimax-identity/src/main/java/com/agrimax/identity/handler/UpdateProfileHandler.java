package com.agrimax.identity.handler;

import com.agrimax.common.voice.CommandHandler;
import com.agrimax.common.voice.CommandResult;
import com.agrimax.common.voice.UIAction;
import com.agrimax.identity.dto.UserDto;
import com.agrimax.identity.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class UpdateProfileHandler implements CommandHandler {

    private final UserService userService;

    @Override
    public String supportedIntent() {
        return "UPDATE_PROFILE";
    }

    @Override
    public CommandResult handle(Map<String, Object> parameters, UUID userId) {
        if (userId == null) {
            return CommandResult.failWithAction(
                    "Please login to update your profile.",
                    "پروفائل اپ ڈیٹ کرنے کے لیے پہلے لاگ ان کریں۔",
                    UIAction.navigate("/login", Map.of("step", "phone")));
        }

        String name = str(parameters.get("name"));
        String languagePref = str(parameters.get("languagePref"));

        // Trim + validate
        if (name != null) name = name.trim();
        if (languagePref != null) languagePref = languagePref.trim().toLowerCase();

        if ((name == null || name.isBlank())
                && (languagePref == null || languagePref.isBlank())) {
            return CommandResult.failWithAction(
                    "What would you like to update — your name or language preference?",
                    "کیا اپ ڈیٹ کرنا ہے — نام یا زبان؟",
                    UIAction.navigate("/profile"));
        }

        if (languagePref != null && !languagePref.isBlank()
                && !languagePref.equals("ur") && !languagePref.equals("en")) {
            return CommandResult.failWithAction(
                    "Language must be Urdu or English.",
                    "زبان اردو یا انگریزی ہونی چاہیے۔",
                    UIAction.navigate("/profile"));
        }

        if (name != null && name.length() > 100) {
            return CommandResult.failWithAction(
                    "That name is too long. Please use a shorter name.",
                    "نام بہت لمبا ہے۔ چھوٹا نام بتائیں۔",
                    UIAction.navigate("/profile"));
        }

        try {
            UserDto updated = userService.updateProfile(
                    userId,
                    (name == null || name.isBlank()) ? null : name,
                    (languagePref == null || languagePref.isBlank()) ? null : languagePref
            );

            String enMsg;
            String urMsg;
            if (name != null && !name.isBlank() && languagePref != null && !languagePref.isBlank()) {
                enMsg = "Your name is now " + updated.name() + " and language set to " +
                        (languagePref.equals("ur") ? "Urdu" : "English") + ".";
                urMsg = "آپ کا نام " + updated.name() + " کر دیا گیا اور زبان " +
                        (languagePref.equals("ur") ? "اردو" : "انگریزی") + " کر دی گئی۔";
            } else if (name != null && !name.isBlank()) {
                enMsg = "Your name is now " + updated.name() + ".";
                urMsg = "آپ کا نام " + updated.name() + " کر دیا گیا۔";
            } else {
                enMsg = "Your preferred language is now " +
                        (languagePref.equals("ur") ? "Urdu" : "English") + ".";
                urMsg = "آپ کی پسندیدہ زبان اب " +
                        (languagePref.equals("ur") ? "اردو" : "انگریزی") + " ہے۔";
            }

            Map<String, Object> data = Map.of(
                    "userId", updated.id().toString(),
                    "name", updated.name() != null ? updated.name() : "",
                    "languagePref", updated.languagePref() != null ? updated.languagePref() : "",
                    "role", updated.role()
            );

            return CommandResult.okWithAction(enMsg, urMsg,
                    UIAction.navigate("/profile"), data);

        } catch (Exception e) {
            log.error("Profile update failed for {}: {}", userId, e.getMessage(), e);
            return CommandResult.failWithAction(
                    "Could not update your profile. Please try again.",
                    "پروفائل اپ ڈیٹ نہیں ہو سکی۔ دوبارہ کوشش کریں۔",
                    UIAction.navigate("/profile"));
        }
    }

    private String str(Object v) { return v == null ? null : v.toString(); }
}