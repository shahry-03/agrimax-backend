package com.agrimax.ai.handler;

import com.agrimax.common.voice.CommandHandler;
import com.agrimax.common.voice.CommandResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Handles HELP intent — tells user what the app can do.
 */
@Slf4j
@Component
public class AppHelpHandler implements CommandHandler {

    @Override
    public String supportedIntent() {
        return "HELP";
    }

    @Override
    public CommandResult handle(Map<String, Object> parameters, UUID userId) {
        String enHelp = """
                I can help you with:
                • Check crop prices — say "wheat price"
                • Sell crops — say "sell 50 kg wheat for 3000"
                • View wallet — say "my wallet"
                • View transactions — say "my payments"
                • Login — say "login with 03001234567"
                • Navigate — say "open market" or "go to wallet"
                """;

        String urHelp = """
                میں یہ کام کر سکتا ہوں:
                • فصل کی قیمت — کہیں "گندم کی قیمت"
                • فصل بیچیں — کہیں "50 کلو گندم 3000 میں بیچنی ہے"
                • والٹ دیکھیں — کہیں "میرا والٹ"
                • لین دین — کہیں "میرے پیمنٹس"
                • لاگ ان — کہیں "لاگ ان 03001234567"
                • صفحہ کھولیں — کہیں "مارکیٹ کھولو"
                """;

        return CommandResult.ok(
                enHelp,
                urHelp,
                Map.of("capabilities", List.of(
                        "GET_CROP_PRICE", "CREATE_SELL_POST",
                        "VIEW_WALLET", "VIEW_PAYMENTS",
                        "LOGIN", "VERIFY_OTP", "NAVIGATE"
                ))
        );
    }
}