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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class CheckOrderStatusHandler implements CommandHandler {

    private final DealService dealService;

    @Override
    public String supportedIntent() {
        return "CHECK_ORDER_STATUS";
    }

    @Override
    public CommandResult handle(Map<String, Object> parameters, UUID userId) {
        if (userId == null) {
            return CommandResult.failWithAction(
                    "Please login to check your order status.",
                    "آرڈر کی صورتحال دیکھنے کے لیے پہلے لاگ ان کریں۔",
                    UIAction.navigate("/login", Map.of("step", "phone")));
        }

        try {
            List<Deal> deals = dealService.getDealsForUser(userId);

            if (deals.isEmpty()) {
                return CommandResult.okWithAction(
                        "You have no active orders right now.",
                        "آپ کے پاس ابھی کوئی فعال آرڈر نہیں ہے۔",
                        UIAction.navigate("/marketplace/deals"),
                        Map.of("total", 0));
            }

            // Count by status
            long pending = deals.stream().filter(d -> "PENDING".equalsIgnoreCase(d.getStatus())).count();
            long completed = deals.stream().filter(d -> "COMPLETED".equalsIgnoreCase(d.getStatus())).count();

            String enMsg;
            String urMsg;
            if (deals.size() == 1) {
                Deal d = deals.get(0);
                enMsg = "You have 1 order — status: " + d.getStatus() + ", amount: PKR " +
                        formatAmount(d.getFinalPrice()) + ".";
                urMsg = "آپ کا 1 آرڈر ہے — صورتحال: " + translateStatus(d.getStatus()) +
                        "، رقم: " + formatAmount(d.getFinalPrice()) + " روپے۔";
            } else {
                enMsg = "You have " + deals.size() + " orders. " +
                        pending + " pending, " + completed + " completed.";
                urMsg = "آپ کے " + deals.size() + " آرڈرز ہیں۔ " +
                        pending + " زیرِ التوا، " + completed + " مکمل۔";
            }

            // Return top 10 for payload
            List<Map<String, Object>> preview = deals.stream()
                    .limit(10)
                    .map(d -> Map.<String, Object>of(
                            "dealId", d.getId().toString(),
                            "status", d.getStatus(),
                            "amount", d.getFinalPrice(),
                            "quantity", d.getQuantity(),
                            "createdAt", d.getCreatedAt().toString()
                    ))
                    .toList();

            Map<String, Object> data = Map.of(
                    "total", deals.size(),
                    "pending", pending,
                    "completed", completed,
                    "preview", preview
            );

            return CommandResult.okWithAction(enMsg, urMsg,
                    UIAction.navigate("/marketplace/deals"),
                    data);

        } catch (Exception e) {
            log.error("Failed to check order status for {}: {}", userId, e.getMessage(), e);
            return CommandResult.failWithAction(
                    "Could not load your orders. Please try again.",
                    "آرڈرز لوڈ نہیں ہو سکے۔ دوبارہ کوشش کریں۔",
                    UIAction.navigate("/marketplace/deals"));
        }
    }

    private String translateStatus(String status) {
        if (status == null) return "نامعلوم";
        return switch (status.toUpperCase()) {
            case "PENDING" -> "زیرِ التوا";
            case "COMPLETED" -> "مکمل";
            case "CANCELLED" -> "منسوخ";
            default -> status;
        };
    }

    private String formatAmount(java.math.BigDecimal amount) {
        if (amount == null) return "0";
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
        nf.setMaximumFractionDigits(2);
        nf.setMinimumFractionDigits(0);
        return nf.format(amount);
    }
}