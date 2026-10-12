package com.agrimax.common.event;

import java.math.BigDecimal;
import java.util.UUID;

/** Published when a bid is placed on a sell post. */
public record BidPlacedEvent(UUID bidId, UUID postId, UUID buyerId, BigDecimal bidPrice) {}
