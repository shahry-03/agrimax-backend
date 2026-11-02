package com.agrimax.common.voice;

import java.util.Set;

public final class SensitiveIntents {

    private SensitiveIntents() {}

    public static final Set<String> REQUIRES_AUTH = Set.of(
            "VIEW_WALLET",
            "VIEW_PAYMENTS",
            "CREATE_SELL_POST",
            "PLACE_BID",
            "CHECK_ORDER_STATUS",
            "UPDATE_PROFILE",
            "VIEW_NOTIFICATIONS",
            "ACCEPT_BID",
            "COMPLETE_DEAL",
            "CANCEL_DEAL",
            "VIEW_MY_BIDS"         // 🆕
    );

    public static boolean requiresAuth(String intent) {
        return REQUIRES_AUTH.contains(intent);
    }
}