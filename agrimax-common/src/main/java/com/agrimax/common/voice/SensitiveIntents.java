package com.agrimax.common.voice;

import java.util.Set;

/**
 * Central registry of voice intents that require authentication.
 * These intents will be rejected with a friendly message if the user is not logged in.
 */
public final class SensitiveIntents {

    private SensitiveIntents() {}

    public static final Set<String> REQUIRES_AUTH = Set.of(
            "VIEW_WALLET",
            "VIEW_PAYMENTS",
            "CREATE_SELL_POST",
            "PLACE_BID",
            "CHECK_ORDER_STATUS",
            "UPDATE_PROFILE",
            "VIEW_NOTIFICATIONS"
    );

    public static boolean requiresAuth(String intent) {
        return REQUIRES_AUTH.contains(intent);
    }
}