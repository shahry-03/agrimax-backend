package com.agrimax.common.event;

import java.math.BigDecimal;
import java.util.UUID;

/** Published when a new sell post is created in the marketplace. */
public record SellPostCreatedEvent(UUID postId, UUID sellerId, UUID cropId,
                                   BigDecimal quantity, BigDecimal askingPrice) {}
