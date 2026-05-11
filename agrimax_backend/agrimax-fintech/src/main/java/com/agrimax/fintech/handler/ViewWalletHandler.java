package com.agrimax.fintech.handler;

import com.agrimax.common.voice.CommandHandler;
import com.agrimax.common.voice.CommandResult;
import com.agrimax.fintech.entity.Wallet;
import com.agrimax.fintech.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ViewWalletHandler implements CommandHandler {

    private final WalletService walletService;

    @Override
    public String supportedIntent() { return "VIEW_WALLET"; }

    @Override
    public CommandResult handle(Map<String, Object> parameters, UUID userId) {
        if (userId == null) return CommandResult.fail("Please login first", "پہلے لاگ ان کریں");
        try {
            Wallet wallet = walletService.getWallet(userId);
            return CommandResult.ok(
                    String.format("Your wallet balance is PKR %s", wallet.getBalance()),
                    String.format("آپ کے والیٹ میں %s روپے ہیں", wallet.getBalance()),
                    Map.of("balance", wallet.getBalance(), "currency", wallet.getCurrency())
            );
        } catch (Exception e) {
            return CommandResult.fail("Wallet not found", "والیٹ نہیں ملا");
        }
    }
}
