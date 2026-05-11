package com.agrimax.marketplace.service;

import com.agrimax.common.event.SellPostCreatedEvent;
import com.agrimax.common.exception.ResourceNotFoundException;
import com.agrimax.marketplace.entity.SellPost;
import com.agrimax.marketplace.repository.SellPostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SellPostService {

    private final SellPostRepository sellPostRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public SellPost createPost(UUID sellerId, UUID cropId, BigDecimal quantity,
                               String unit, BigDecimal askingPrice) {
        SellPost post = SellPost.builder()
                .sellerId(sellerId)
                .cropId(cropId)
                .quantity(quantity)
                .unit(unit != null ? unit : "kg")
                .askingPrice(askingPrice)
                .status("ACTIVE")
                .expiresAt(Instant.now().plus(30, ChronoUnit.DAYS))
                .build();

        post = sellPostRepository.save(post);
        log.info("Sell post created: {} by seller {}", post.getId(), sellerId);

        eventPublisher.publishEvent(
                new SellPostCreatedEvent(post.getId(), sellerId, cropId, quantity, askingPrice)
        );

        return post;
    }

    @Transactional(readOnly = true)
    public List<SellPost> getPostsBySeller(UUID sellerId) {
        return sellPostRepository.findBySellerIdOrderByCreatedAtDesc(sellerId);
    }

    @Transactional(readOnly = true)
    public List<SellPost> getActivePosts() {
        return sellPostRepository.findByStatusOrderByCreatedAtDesc("ACTIVE");
    }

    @Transactional(readOnly = true)
    public SellPost getPostById(UUID postId) {
        return sellPostRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + postId));
    }
}
