package com.agrimax.common.event;

import java.math.BigDecimal;
import java.util.UUID;

/** Published when a seller accepts a bid (deal created + escrow held). */
public record BidAcceptedEvent(
        UUID bidId,
        UUID dealId,
        UUID sellerId,
        UUID buyerId,
        BigDecimal amount
) {}