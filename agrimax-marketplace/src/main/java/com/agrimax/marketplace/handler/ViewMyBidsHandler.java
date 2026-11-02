package com.agrimax.marketplace.handler;

import com.agrimax.common.voice.CommandHandler;
import com.agrimax.common.voice.CommandResult;
import com.agrimax.common.voice.UIAction;
import com.agrimax.identity.service.UserService;
import com.agrimax.marketplace.entity.Bid;
import com.agrimax.marketplace.service.BidService;
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
public class ViewMyBidsHandler implements CommandHandler {

    private final BidService bidService;
    private final UserService userService;

    @Override
    public String supportedIntent() {
        return "VIEW_MY_BIDS";
    }

    @Override
    public CommandResult handle(Map<String, Object> parameters, UUID userId) {
        if (userId == null) {
            return CommandResult.failWithAction(
                    "Please login to view your bids.",
                    "اپنی bids دیکھنے کے لیے پہلے لاگ ان کریں۔",
                    UIAction.navigate("/login", Map.of("step", "phone")));
        }

        if (!userService.hasRole(userId, "BUYER")) {
            log.warn("Non-buyer attempted VIEW_MY_BIDS: userId={}", userId);
            return CommandResult.failWithAction(
                    "Only buyers have bids. Farmers create sell posts instead.",
                    "صرف خریداروں کی bids ہوتی ہیں۔ کسان پوسٹ بناتے ہیں۔",
                    UIAction.navigate("/marketplace"));
        }

        try {
            List<Bid> bids = bidService.getMyBids(userId);

            if (bids.isEmpty()) {
                return CommandResult.okWithAction(
                        "You have no bids yet. Open a marketplace post to place your first bid.",
                        "آپ کی ابھی کوئی bid نہیں ہے۔ پہلی bid لگانے کے لیے مارکیٹ پوسٹ کھولیں۔",
                        UIAction.navigate("/marketplace"),
                        Map.of("total", 0, "bids", List.of()));
            }

            long pending = bids.stream()
                    .filter(b -> "PENDING".equalsIgnoreCase(b.getStatus())).count();
            long accepted = bids.stream()
                    .filter(b -> "ACCEPTED".equalsIgnoreCase(b.getStatus())).count();
            long rejected = bids.stream()
                    .filter(b -> "REJECTED".equalsIgnoreCase(b.getStatus())).count();

            String enMsg;
            String urMsg;
            if (bids.size() == 1) {
                Bid b = bids.get(0);
                enMsg = "You have 1 bid — status: " + b.getStatus()
                        + ", price: PKR " + formatAmount(b.getBidPrice()) + ".";
                urMsg = "آپ کی 1 bid ہے — صورتحال: " + translateStatus(b.getStatus())
                        + "، قیمت: " + formatAmount(b.getBidPrice()) + " روپے۔";
            } else {
                enMsg = "You have " + bids.size() + " bids. "
                        + pending + " pending, " + accepted + " accepted, "
                        + rejected + " rejected.";
                urMsg = "آپ کی " + bids.size() + " bids ہیں۔ "
                        + pending + " زیرِ التوا، " + accepted + " قبول، "
                        + rejected + " مسترد۔";
            }

            List<Map<String, Object>> preview = bids.stream()
                    .limit(10)
                    .map(b -> Map.<String, Object>of(
                            "bidId", b.getId().toString(),
                            "postId", b.getPost().getId().toString(),
                            "bidPrice", b.getBidPrice(),
                            "status", b.getStatus(),
                            "createdAt", b.getCreatedAt().toString()))
                    .toList();

            Map<String, Object> data = Map.of(
                    "total", bids.size(),
                    "pending", pending,
                    "accepted", accepted,
                    "rejected", rejected,
                    "bids", preview);

            return CommandResult.okWithAction(enMsg, urMsg,
                    UIAction.navigate("/my-bids", Map.of("source", "voice")),
                    data);

        } catch (Exception e) {
            log.error("Failed to load bids for {}: {}", userId, e.getMessage(), e);
            return CommandResult.failWithAction(
                    "Could not load your bids. Please try again.",
                    "آپ کی bids لوڈ نہیں ہو سکیں۔ دوبارہ کوشش کریں۔",
                    UIAction.navigate("/my-bids"));
        }
    }

    private String translateStatus(String s) {
        if (s == null) return "نامعلوم";
        return switch (s.toUpperCase()) {
            case "PENDING" -> "زیرِ التوا";
            case "ACCEPTED" -> "قبول";
            case "REJECTED" -> "مسترد";
            default -> s;
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