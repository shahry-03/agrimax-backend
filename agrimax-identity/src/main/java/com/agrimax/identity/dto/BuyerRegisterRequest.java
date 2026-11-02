package com.agrimax.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record BuyerRegisterRequest(
        @NotBlank @Email @Size(max = 300) String email,
        @NotBlank
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*?&]).{8,}$",
                message = "Password must be 8+ chars with letter, digit, and special char")
        String password,
        @NotBlank @Size(max = 500) String name
) {}