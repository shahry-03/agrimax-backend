package com.agrimax.fintech.handler;

import com.agrimax.common.voice.CommandHandler;
import com.agrimax.common.voice.CommandResult;
import com.agrimax.fintech.entity.Transaction;
import com.agrimax.fintech.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ViewPaymentsHandler implements CommandHandler {

    private final WalletService walletService;

    @Override
    public String supportedIntent() { return "VIEW_PAYMENTS"; }

    @Override
    public CommandResult handle(Map<String, Object> parameters, UUID userId) {
        if (userId == null) return CommandResult.fail("Please login first", "پہلے لاگ ان کریں");
        try {
            List<Transaction> txns = walletService.getTransactions(userId);
            if (txns.isEmpty()) {
                return CommandResult.ok("No transactions yet", "ابھی تک کوئی لین دین نہیں", List.of());
            }
            return CommandResult.ok(
                    String.format("You have %d transactions", txns.size()),
                    String.format("آپ کے %d لین دین ہیں", txns.size()),
                    txns.stream().map(t -> Map.of(
                            "type", t.getType(), "amount", t.getAmount(),
                            "description", t.getDescription() != null ? t.getDescription() : "",
                            "date", t.getCreatedAt().toString()
                    )).toList()
            );
        } catch (Exception e) {
            return CommandResult.fail("Could not load payments", "ادائیگیاں لوڈ نہیں ہوسکیں");
        }
    }
}
