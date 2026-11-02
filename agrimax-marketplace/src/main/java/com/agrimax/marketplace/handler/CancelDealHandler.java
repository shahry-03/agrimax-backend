package com.agrimax.marketplace.handler;

import com.agrimax.common.voice.CommandHandler;
import com.agrimax.common.voice.CommandResult;
import com.agrimax.common.voice.UIAction;
import com.agrimax.marketplace.entity.Deal;
import com.agrimax.marketplace.service.DealService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class CancelDealHandler implements CommandHandler {

    private final DealService dealService;

    @Override
    public String supportedIntent() { return "CANCEL_DEAL"; }

    @Override
    public CommandResult handle(Map<String, Object> parameters, UUID userId) {
        if (userId == null) {
            return CommandResult.failWithAction(
                    "Please login to cancel a deal.",
                    "سودا منسوخ کرنے کے لیے لاگ ان کریں۔",
                    UIAction.navigate("/login", Map.of("step", "phone")));
        }

        String dealIdStr = (String) parameters.get("dealId");
        if (dealIdStr == null || dealIdStr.isBlank()) {
            return CommandResult.failWithAction(
                    "Please open a deal first, then tell me to cancel it.",
                    "پہلے کوئی سودا کھولیں، پھر منسوخ کرنے کو کہیں۔",
                    UIAction.navigate("/marketplace/deals"));
        }

        try {
            UUID dealId = UUID.fromString(dealIdStr);
            Deal deal = dealService.cancelDeal(dealId, userId);

            String price = formatAmount(deal.getFinalPrice());
            return CommandResult.okWithAction(
                    "Deal cancelled. The buyer has been refunded PKR " + price + ".",
                    "سودا منسوخ۔ خریدار کو " + price + " روپے واپس کر دیے گئے۔",
                    UIAction.navigate("/marketplace/deals"),
                    Map.of(
                            "dealId", dealId.toString(),
                            "status", deal.getStatus(),
                            "refunded", deal.getFinalPrice()
                    ));

        } catch (Exception e) {
            log.error("Cancel deal error: {}", e.getMessage(), e);
            return CommandResult.failWithAction(
                    "Could not cancel the deal.", "سودا منسوخ نہیں ہو سکا۔",
                    UIAction.navigate("/marketplace/deals"));
        }
    }

    private String formatAmount(java.math.BigDecimal amount) {
        if (amount == null) return "0";
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
        nf.setMaximumFractionDigits(2);
        nf.setMinimumFractionDigits(0);
        return nf.format(amount);
    }
}