package com.agrimax.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SendOtpRequest(
        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^03[0-9]{9}$", message = "Invalid Pakistani phone number")
        String phoneNumber
) {}
