package com.agrimax.common.event;

import java.math.BigDecimal;
import java.util.UUID;

/** Published when a deal is completed between buyer and seller. */
public record DealCompletedEvent(UUID dealId, UUID sellerId, UUID buyerId, BigDecimal amount) {}
