
package com.agrimax.fintech.handler;

import com.agrimax.common.voice.CommandHandler;
import com.agrimax.common.voice.CommandResult;
import com.agrimax.common.voice.UIAction;
import com.agrimax.fintech.entity.Transaction;
import com.agrimax.fintech.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ViewPaymentsHandler implements CommandHandler {

    private final WalletService walletService;

    @Override
    public String supportedIntent() {
        return "VIEW_PAYMENTS";
    }

    @Override
    public CommandResult handle(Map<String, Object> parameters, UUID userId) {

        // 1. Authentication check
        if (userId == null) {
            return CommandResult.failWithAction(
                    "Please login first",
                    "پہلے لاگ ان کریں",
                    UIAction.navigate("/login", Map.of("step", "phone"))
            );
        }

        try {
            // 2. Fetch user transactions
            List<Transaction> txns = walletService.getTransactions(userId);

            // 3. Handle empty transaction history
            if (txns.isEmpty()) {
                return CommandResult.okWithAction(
                        "No transactions yet",
                        "ابھی تک کوئی لین دین نہیں",
                        UIAction.navigate(
                                "/wallet",
                                Map.of("tab", "transactions")
                        ),
                        List.of()
                );
            }

            // 4. Return transaction history
            return CommandResult.okWithAction(
                    String.format(
                            "You have %d transactions",
                            txns.size()
                    ),
                    String.format(
                            "آپ کے %d لین دین ہیں",
                            txns.size()
                    ),
                    UIAction.navigate(
                            "/wallet",
                            Map.of("tab", "transactions")
                    ),
                    txns.stream().map(t -> Map.of(
                            "type", t.getType(),
                            "amount", t.getAmount(),
                            "description", t.getDescription() != null
                                    ? t.getDescription()
                                    : "",
                            "date", t.getCreatedAt().toString()
                    )).toList()
            );

        } catch (Exception e) {

            log.error(
                    "Failed to load payments for user {}: {}",
                    userId,
                    e.getMessage(),
                    e
            );

            // 5. Handle exception
            return CommandResult.failWithAction(
                    "Could not load payments",
                    "ادائیگیاں لوڈ نہیں ہوسکیں",
                    UIAction.navigate(
                            "/wallet",
                            Map.of("tab", "transactions")
                    )
            );
        }
    }
}
