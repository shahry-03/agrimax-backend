package com.agrimax.marketplace.service;

import com.agrimax.fintech.service.EscrowService;
import com.agrimax.marketplace.entity.Deal;
import com.agrimax.marketplace.entity.SellPost;
import com.agrimax.marketplace.repository.BidRepository;
import com.agrimax.marketplace.repository.DealRepository;
import com.agrimax.marketplace.repository.SellPostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DealServiceTest {

    @Mock private DealRepository dealRepository;
    @Mock private SellPostRepository sellPostRepository;
    @Mock private BidRepository bidRepository;
    @Mock private EscrowService escrowService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private DealService dealService;

    private UUID sellerId;
    private UUID buyerId;
    private UUID dealId;
    private Deal pendingDeal;
    private SellPost sellPost;

    @BeforeEach
    void setUp() {
        dealService = new DealService(
                dealRepository,
                sellPostRepository,
                bidRepository,
                escrowService,
                eventPublisher
        );

        sellerId = UUID.randomUUID();
        buyerId = UUID.randomUUID();
        dealId = UUID.randomUUID();

        sellPost = SellPost.builder()
                .id(UUID.randomUUID())
                .sellerId(sellerId)
                .status("SOLD")
                .quantity(new BigDecimal("100"))
                .askingPrice(new BigDecimal("3000"))
                .build();

        pendingDeal = Deal.builder()
                .id(dealId)
                .sellerId(sellerId)
                .buyerId(buyerId)
                .post(sellPost)
                .status("PENDING")
                .finalPrice(new BigDecimal("2900"))
                .quantity(new BigDecimal("100"))
                .build();
    }

    @Test
    void testCompleteDealReleasesEscrow() {
        when(dealRepository.findById(dealId)).thenReturn(Optional.of(pendingDeal));
        when(dealRepository.save(any(Deal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Deal completed = dealService.completeDeal(dealId, buyerId);

        assertEquals("COMPLETED", completed.getStatus());
        assertNotNull(completed.getCompletedAt());
        verify(escrowService).releaseFunds(dealId);
        verify(eventPublisher).publishEvent(any(Object.class));
    }

    @Test
    void testCancelDealRefundsEscrowAndRelistsPost() {
        when(dealRepository.findById(dealId)).thenReturn(Optional.of(pendingDeal));
        when(dealRepository.save(any(Deal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Deal cancelled = dealService.cancelDeal(dealId, buyerId);

        assertEquals("CANCELLED", cancelled.getStatus());
        verify(escrowService).refund(dealId);
        verify(sellPostRepository).save(sellPost);
        assertEquals("ACTIVE", sellPost.getStatus());
        verify(eventPublisher).publishEvent(any(Object.class));
    }
}
