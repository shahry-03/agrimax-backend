package com.agrimax.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VerifyOtpRequest(
        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^03[0-9]{9}$", message = "Invalid Pakistani phone number")
        String phoneNumber,

        @NotBlank(message = "OTP is required")
        @Size(min = 6, max = 6, message = "OTP must be 6 digits")
        String otp
) {}
