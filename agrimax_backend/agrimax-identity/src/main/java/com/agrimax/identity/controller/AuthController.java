package com.agrimax.identity.controller;

import com.agrimax.common.dto.ApiResponse;
import com.agrimax.identity.dto.AuthResponse;
import com.agrimax.identity.dto.SendOtpRequest;
import com.agrimax.identity.dto.VerifyOtpRequest;
import com.agrimax.identity.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/send-otp")
    public ResponseEntity<ApiResponse<Void>> sendOtp(@Valid @RequestBody SendOtpRequest request) {
        authService.sendOtp(request);
        return ResponseEntity.ok(
                ApiResponse.success("OTP has been sent to your phone", "OTP آپ کے فون پر بھیج دیا گیا ہے")
        );
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        AuthResponse response = authService.verifyOtp(request);
        return ResponseEntity.ok(
                ApiResponse.success(response, "Login successful", "لاگ ان کامیاب")
        );
    }
}
