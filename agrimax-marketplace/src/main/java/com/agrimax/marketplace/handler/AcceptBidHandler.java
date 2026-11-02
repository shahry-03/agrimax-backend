package com.agrimax.marketplace.handler;

import com.agrimax.common.exception.BadRequestException;
import com.agrimax.common.exception.ResourceNotFoundException;
import com.agrimax.common.exception.UnauthorizedException;
import com.agrimax.common.voice.CommandHandler;
import com.agrimax.common.voice.CommandResult;
import com.agrimax.common.voice.UIAction;
import com.agrimax.marketplace.entity.Deal;
import com.agrimax.marketplace.service.BidService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AcceptBidHandler implements CommandHandler {

    private final BidService bidService;

    @Override
    public String supportedIntent() { return "ACCEPT_BID"; }

    @Override
    public CommandResult handle(Map<String, Object> parameters, UUID userId) {
        if (userId == null) {
            return CommandResult.failWithAction(
                    "Please login first to accept bids.",
                    "bid قبول کرنے کے لیے پہلے لاگ ان کریں۔",
                    UIAction.navigate("/login", Map.of("step", "phone")));
        }

        String bidIdStr = (String) parameters.get("bidId");
        if (bidIdStr == null || bidIdStr.isBlank()) {
            return CommandResult.failWithAction(
                    "Please open a bid first, then tell me to accept it.",
                    "پہلے کوئی bid کھولیں، پھر قبول کرنے کو کہیں۔",
                    UIAction.navigate("/marketplace"));
        }

        try {
            UUID bidId = UUID.fromString(bidIdStr);
            Deal deal = bidService.acceptBid(bidId, userId);

            String formattedPrice = formatAmount(deal.getFinalPrice());
            String enMsg = "Bid accepted! Deal created for PKR " + formattedPrice +
                    ". The buyer's payment is held in escrow.";
            String urMsg = "bid قبول! " + formattedPrice +
                    " روپے کا سودا بن گیا۔ خریدار کی ادائیگی escrow میں محفوظ ہے۔";

            Map<String, Object> data = new HashMap<>();
            data.put("dealId", deal.getId().toString());
            data.put("finalPrice", deal.getFinalPrice());
            data.put("status", deal.getStatus());

            return CommandResult.okWithAction(enMsg, urMsg,
                    UIAction.navigate("/marketplace/deals/" + deal.getId()),
                    data);

        } catch (IllegalArgumentException e) {
            return CommandResult.failWithAction("Invalid bid ID.", "غلط bid ID۔",
                    UIAction.navigate("/marketplace"));
        } catch (UnauthorizedException e) {
            return CommandResult.failWithAction(
                    "This is not your post.", "یہ آپ کی پوسٹ نہیں ہے۔", UIAction.none());
        } catch (ResourceNotFoundException | BadRequestException e) {
            log.warn("Accept bid failed: {}", e.getMessage());
            return CommandResult.failWithAction(e.getMessage(),
                    "bid قبول نہیں ہو سکی۔", UIAction.navigate("/marketplace"));
        } catch (Exception e) {
            log.error("Accept bid error: {}", e.getMessage(), e);
            return CommandResult.failWithAction(
                    "Could not accept bid. Try again.", "bid قبول نہیں ہو سکی۔",
                    UIAction.none());
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