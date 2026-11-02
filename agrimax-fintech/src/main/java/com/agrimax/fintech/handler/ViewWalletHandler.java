
package com.agrimax.fintech.handler;

import com.agrimax.common.voice.CommandHandler;
import com.agrimax.common.voice.CommandResult;
import com.agrimax.common.voice.UIAction;
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

/**
 * Handles VIEW_WALLET voice intent.
 *
 * Retrieves the authenticated user's wallet balance
 * and navigates to the wallet page.
 */
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

        // 1. Authentication check
        if (userId == null) {
            return CommandResult.failWithAction(
                    "Please login first to view your wallet.",
                    "والیٹ دیکھنے کے لیے پہلے لاگ ان کریں۔",
                    UIAction.navigate("/login", Map.of("step", "phone"))
            );
        }

        try {
            // 2. Auto-create wallet if it doesn't exist
            Wallet wallet = walletService.getOrCreateWallet(userId);

            BigDecimal balance = wallet.getBalance();
            String currency = wallet.getCurrency() != null
                    ? wallet.getCurrency()
                    : "PKR";

            String formattedBalance = formatAmount(balance);

            // 3. Prepare bilingual messages
            String enMsg;
            String urMsg;

            if (balance.compareTo(BigDecimal.ZERO) == 0) {

                enMsg = String.format(
                        "Your wallet balance is %s %s. Sell a crop to start earning!",
                        currency,
                        formattedBalance
                );

                urMsg = String.format(
                        "آپ کے والیٹ میں %s %s ہیں۔ کمانے کے لیے اپنی فصل بیچیں!",
                        formattedBalance,
                        currency
                );

            } else {

                enMsg = String.format(
                        "Your wallet balance is %s %s.",
                        currency,
                        formattedBalance
                );

                urMsg = String.format(
                        "آپ کے والیٹ میں %s %s ہیں۔",
                        formattedBalance,
                        currency
                );
            }

            // 4. Prepare wallet response data
            Map<String, Object> data = new HashMap<>();

            data.put("balance", balance);
            data.put("currency", currency);
            data.put("walletId", wallet.getId());
            data.put("isActive", wallet.getIsActive());

            log.info(
                    "Wallet fetched successfully for user {} | balance: {} {}",
                    userId,
                    formattedBalance,
                    currency
            );

            // 5. Success — navigate to wallet page
            return CommandResult.okWithAction(
                    enMsg,
                    urMsg,
                    UIAction.navigate(
                            "/wallet",
                            Map.of("source", "voice")
                    ),
                    data
            );

        } catch (Exception e) {

            log.error(
                    "Failed to fetch wallet for user {}: {}",
                    userId,
                    e.getMessage(),
                    e
            );

            // 6. Exception — navigate to wallet page
            return CommandResult.failWithAction(
                    "Could not load your wallet. Please try again.",
                    "آپ کا والیٹ لوڈ نہیں ہو سکا۔ دوبارہ کوشش کریں۔",
                    UIAction.navigate("/wallet")
            );
        }
    }

    /**
     * Format balance nicely:
     * 5000      → "5,000"
     * 5000.50   → "5,000.5"
     */
    private String formatAmount(BigDecimal amount) {

        if (amount == null) {
            return "0";
        }

        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
        nf.setMaximumFractionDigits(2);
        nf.setMinimumFractionDigits(0);

        return nf.format(amount);
    }
}
