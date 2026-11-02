package com.agrimax.marketplace.handler;

import com.agrimax.common.exception.BadRequestException;
import com.agrimax.common.exception.ResourceNotFoundException;
import com.agrimax.common.voice.CommandHandler;
import com.agrimax.common.voice.CommandResult;
import com.agrimax.common.voice.UIAction;
import com.agrimax.identity.service.UserService;
import com.agrimax.marketplace.entity.Bid;
import com.agrimax.marketplace.service.BidService;
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
public class PlaceBidHandler implements CommandHandler {

    private final BidService bidService;
    private final UserService userService;   // 🆕

    @Override
    public String supportedIntent() {
        return "PLACE_BID";
    }

    @Override
    public CommandResult handle(Map<String, Object> parameters, UUID userId) {
        // 1. Auth
        if (userId == null) {
            return CommandResult.failWithAction(
                    "Please login first to place a bid.",
                    "bid لگانے کے لیے پہلے لاگ ان کریں۔",
                    UIAction.navigate("/login", Map.of("step", "phone")));
        }

        // 2. Role check — only BUYER can place bids
        if (!userService.hasRole(userId, "BUYER")) {
            log.warn("Non-buyer attempted PLACE_BID: userId={}", userId);
            return CommandResult.failWithAction(
                    "Only buyers can place bids. Farmers create sell posts instead.",
                    "صرف خریدار bid لگا سکتے ہیں۔ کسان اپنی فصل کی پوسٹ بناتے ہیں۔",
                    UIAction.navigate("/marketplace"));
        }

        // 3. Post ID
        String postIdStr = str(parameters.get("postId"));
        if (postIdStr == null || postIdStr.isBlank()) {
            return CommandResult.failWithAction(
                    "Please open a crop post first, then tell me your bid price.",
                    "پہلے کوئی فصل کی پوسٹ کھولیں، پھر اپنی قیمت بتائیں۔",
                    UIAction.navigate("/marketplace"));
        }

        // 4. Bid price
        BigDecimal bidPrice = toBigDecimal(parameters.get("bidPrice"));
        if (bidPrice == null || bidPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return CommandResult.failWithAction(
                    "What price would you like to bid?",
                    "آپ کتنی قیمت کی bid لگانا چاہتے ہیں؟",
                    UIAction.none());
        }

        String message = str(parameters.get("message"));

        try {
            UUID postId = UUID.fromString(postIdStr);
            Bid bid = bidService.placeBid(postId, userId, bidPrice, message);

            String formattedPrice = formatAmount(bidPrice);
            Map<String, Object> data = new HashMap<>();
            data.put("bidId", bid.getId().toString());
            data.put("postId", postIdStr);
            data.put("bidPrice", bidPrice);
            data.put("status", bid.getStatus());

            return CommandResult.okWithAction(
                    "Your bid of PKR " + formattedPrice + " has been placed. The seller will be notified.",
                    "آپ کی " + formattedPrice + " روپے کی bid لگا دی گئی ہے۔ بیچنے والے کو اطلاع دے دی جائے گی۔",
                    UIAction.navigate("/marketplace/posts/" + postIdStr),
                    data);

        } catch (IllegalArgumentException e) {
            return CommandResult.failWithAction("Invalid post ID.", "غلط پوسٹ ID۔",
                    UIAction.navigate("/marketplace"));
        } catch (ResourceNotFoundException e) {
            return CommandResult.failWithAction(
                    "That post could not be found. It may have been removed.",
                    "وہ پوسٹ نہیں مل سکی۔ ہو سکتا ہے ہٹا دی گئی ہو۔",
                    UIAction.navigate("/marketplace"));
        } catch (BadRequestException e) {
            log.warn("Bid rejected: {}", e.getMessage());
            return CommandResult.failWithAction(
                    e.getMessage(), translateError(e.getMessage()), UIAction.none());
        } catch (Exception e) {
            log.error("Bid placement failed for {}: {}", userId, e.getMessage(), e);
            return CommandResult.failWithAction(
                    "Could not place your bid. Please try again.",
                    "bid نہیں لگ سکی۔ دوبارہ کوشش کریں۔",
                    UIAction.none());
        }
    }

    private String translateError(String en) {
        if (en == null) return "کچھ غلط ہو گیا";
        if (en.contains("own post")) return "آپ اپنی پوسٹ پر bid نہیں لگا سکتے";
        if (en.contains("not active")) return "یہ پوسٹ اب فعال نہیں ہے";
        if (en.contains("expired")) return "یہ پوسٹ ختم ہو گئی ہے";
        if (en.contains("greater than 0")) return "قیمت صفر سے زیادہ ہونی چاہیے";
        return "کچھ غلط ہو گیا";
    }

    private String str(Object v) { return v == null ? null : v.toString(); }

    private BigDecimal toBigDecimal(Object v) {
        if (v == null) return null;
        try { return new BigDecimal(v.toString()); }
        catch (NumberFormatException e) { return null; }
    }

    private String formatAmount(BigDecimal amount) {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
        nf.setMaximumFractionDigits(2);
        nf.setMinimumFractionDigits(0);
        return nf.format(amount);
    }
}