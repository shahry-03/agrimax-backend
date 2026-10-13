package com.agrimax.fintech.handler;

import com.agrimax.common.voice.CommandHandler;
import com.agrimax.common.voice.CommandResult;
import com.agrimax.fintech.entity.Wallet;
import com.agrimax.fintech.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ViewWalletHandler implements CommandHandler {

    private final WalletService walletService;

    @Override
    public String supportedIntent() {
        return "VIEW_WALLET";
    }

    @Override
    public CommandResult handle(Map<String, Object> parameters, UUID userId) {
        // Defensive check (also enforced centrally by CommandRouterService)
        if (userId == null) {
            return CommandResult.fail(
                    "Please login first to view your wallet.",
                    "والیٹ دیکھنے کے لیے پہلے لاگ ان کریں۔"
            );
        }

        try {
            // Auto-create wallet if it doesn't exist (safety net for old users)
            Wallet wallet = walletService.getOrCreateWallet(userId);

            BigDecimal balance = wallet.getBalance();
            String currency = wallet.getCurrency() != null ? wallet.getCurrency() : "PKR";
            String formattedBalance = formatAmount(balance);

            String enMsg;
            String urMsg;

            if (balance.compareTo(BigDecimal.ZERO) == 0) {
                enMsg = String.format(
                        "Your wallet balance is %s %s. Sell a crop to start earning!",
                        currency, formattedBalance);
                urMsg = String.format(
                        "آپ کے والیٹ میں %s %s ہیں۔ کمانے کے لیے اپنی فصل بیچیں!",
                        formattedBalance, currency);
            } else {
                enMsg = String.format("Your wallet balance is %s %s.", currency, formattedBalance);
                urMsg = String.format("آپ کے والیٹ میں %s %s ہیں۔", formattedBalance, currency);
            }

            Map<String, Object> data = new HashMap<>();
            data.put("balance", balance);
            data.put("currency", currency);
            data.put("walletId", wallet.getId());
            data.put("isActive", wallet.getIsActive());

            return CommandResult.ok(enMsg, urMsg, data);

        } catch (Exception e) {
            log.error("Failed to fetch wallet for user {}: {}", userId, e.getMessage(), e);
            return CommandResult.fail(
                    "Could not load your wallet. Please try again.",
                    "آپ کا والیٹ لوڈ نہیں ہو سکا۔ دوبارہ کوشش کریں۔"
            );
        }
    }

    /** Format balance nicely: 5000 → "5,000" ; 5000.50 → "5,000.50" */
    private String formatAmount(BigDecimal amount) {
        if (amount == null) return "0";
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
        nf.setMaximumFractionDigits(2);
        nf.setMinimumFractionDigits(0);
        return nf.format(amount);
    }
}