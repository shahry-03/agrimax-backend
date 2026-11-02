package com.agrimax.common.event;

import java.math.BigDecimal;
import java.util.UUID;

/** Published when a deal is cancelled (escrow refunded to buyer). */
public record DealCancelledEvent(
        UUID dealId,
        UUID sellerId,
        UUID buyerId,
        BigDecimal refundedAmount
) {}