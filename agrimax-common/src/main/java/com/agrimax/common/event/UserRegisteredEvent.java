package com.agrimax.common.event;

import java.util.UUID;

/** Published when a new user registers via OTP verification. */
public record UserRegisteredEvent(UUID userId, String phoneNumber, String role) {}
