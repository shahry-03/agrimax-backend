package com.agrimax.fintech.listener;

import com.agrimax.common.event.UserRegisteredEvent;
import com.agrimax.fintech.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Listens for user registration events and auto-creates a wallet.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WalletEventListener {

    private final WalletService walletService;

    @Async
    @EventListener
    public void onUserRegistered(UserRegisteredEvent event) {
        log.info("Creating wallet for newly registered user: {}", event.userId());
        walletService.createWallet(event.userId());
    }
}
