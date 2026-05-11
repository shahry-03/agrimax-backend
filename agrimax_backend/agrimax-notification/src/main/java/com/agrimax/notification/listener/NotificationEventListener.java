package com.agrimax.notification.listener;

import com.agrimax.common.event.DealCompletedEvent;
import com.agrimax.common.event.SellPostCreatedEvent;
import com.agrimax.common.event.UserRegisteredEvent;
import com.agrimax.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Listens to cross-module events and creates notifications.
 * Runs async to avoid blocking the publishing module.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;

    @Async
    @EventListener
    public void onUserRegistered(UserRegisteredEvent event) {
        log.info("Event received: UserRegistered for {}", event.userId());
        notificationService.send(event.userId(), "IN_APP",
                "Welcome to AgriMax!", "AgriMax میں خوش آمدید!",
                "Your account has been created. Start selling your crops now!",
                "آپ کا اکاؤنٹ بن گیا ہے۔ ابھی اپنی فصل بیچنا شروع کریں!",
                Map.of("event", "USER_REGISTERED")
        );
    }

    @Async
    @EventListener
    public void onSellPostCreated(SellPostCreatedEvent event) {
        log.info("Event received: SellPostCreated {}", event.postId());
        notificationService.send(event.sellerId(), "IN_APP",
                "Post Created", "پوسٹ بن گئی",
                "Your crop listing is now live in the marketplace.",
                "آپ کی فصل کی پوسٹ مارکیٹ میں لگا دی گئی ہے۔",
                Map.of("event", "SELL_POST_CREATED", "postId", event.postId().toString())
        );
    }

    @Async
    @EventListener
    public void onDealCompleted(DealCompletedEvent event) {
        log.info("Event received: DealCompleted {}", event.dealId());
        // Notify seller
        notificationService.send(event.sellerId(), "IN_APP",
                "Deal Completed!", "سودا مکمل!",
                "Your deal has been completed. Payment of PKR " + event.amount() + " is on its way.",
                "آپ کا سودا مکمل ہوگیا۔ " + event.amount() + " روپے کی ادائیگی آ رہی ہے۔",
                Map.of("event", "DEAL_COMPLETED", "dealId", event.dealId().toString())
        );
        // Notify buyer
        notificationService.send(event.buyerId(), "IN_APP",
                "Purchase Confirmed", "خریداری کی تصدیق",
                "Your purchase has been confirmed.",
                "آپ کی خریداری کی تصدیق ہو گئی ہے۔",
                Map.of("event", "DEAL_COMPLETED", "dealId", event.dealId().toString())
        );
    }
}
