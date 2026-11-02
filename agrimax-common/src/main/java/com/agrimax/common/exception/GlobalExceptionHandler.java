package com.agrimax.common.exception;

import com.agrimax.common.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage(), "Resource not found", "معلومات نہیں ملی"));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(BadRequestException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage(), "Invalid request", "غلط درخواست"));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorized(UnauthorizedException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(ex.getMessage(), "Unauthorized", "غیر مجاز رسائی"));
    }

    @ExceptionHandler(UnsupportedIntentException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnsupportedIntent(UnsupportedIntentException ex) {
        log.warn("Unsupported intent: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage(),
                        "I don't understand this command",
                        "یہ حکم سمجھ نہیں آیا"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR",
                        "Something went wrong. Please try again.",
                        "کچھ غلط ہو گیا۔ دوبارہ کوشش کریں۔"));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleRateLimit(RateLimitExceededException ex) {
        log.warn("Rate limit exceeded: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .body(ApiResponse.error("RATE_LIMITED",
                        "Too many requests. Please try again in " + ex.getRetryAfterSeconds() + " seconds.",
                        "بہت زیادہ درخواستیں۔ براہ کرم کچھ دیر بعد کوشش کریں۔"));
    }

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccountLocked(AccountLockedException ex) {
        log.warn("Account locked: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.LOCKED)
                .body(ApiResponse.error("ACCOUNT_LOCKED",
                        ex.getMessage(),
                        "اکاؤنٹ عارضی طور پر لاک ہے۔ براہ کرم بعد میں کوشش کریں۔"));
    }

    

        // ═══════════════════════════════════════════════════════
        // 🆕 Security / auth exceptions
        // ═══════════════════════════════════════════════════════

        @ExceptionHandler(EmailNotVerifiedException.class)
        public ResponseEntity<ApiResponse<Void>> handleEmailNotVerified(EmailNotVerifiedException ex) {
        log.warn("Email not verified: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("EMAIL_NOT_VERIFIED",
                        ex.getMessage(),
                        "براہ کرم پہلے اپنا ای میل تصدیق کریں۔"));
        }

        @ExceptionHandler(DisabledException.class)
        public ResponseEntity<ApiResponse<Void>> handleDisabled(DisabledException ex) {
        log.warn("Account disabled: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("ACCOUNT_DISABLED",
                        "Your account is disabled. Please contact support.",
                        "آپ کا اکاؤنٹ غیر فعال ہے۔ براہ کرم مدد سے رابطہ کریں۔"));
        }

        @ExceptionHandler(AuthenticationException.class)
        public ResponseEntity<ApiResponse<Void>> handleAuthException(AuthenticationException ex) {
        log.warn("Authentication failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("UNAUTHORIZED",
                        "Invalid credentials",
                        "غلط ای میل یا پاس ورڈ"));
        }

        @ExceptionHandler(AccessDeniedException.class)
        public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("FORBIDDEN",
                        "You don't have permission for this action",
                        "آپ کو اس کام کی اجازت نہیں ہے"));
        }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException ex) {
        String errors = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn("Validation failed: {}", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("VALIDATION_ERROR",
                        errors,
                        "فارم میں غلطی ہے، براہ کرم معلومات درست کریں"));
        }

        @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
        public ResponseEntity<ApiResponse<Void>> handleIllegalState(RuntimeException ex) {
        log.warn("Illegal argument/state: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("BAD_REQUEST",
                        ex.getMessage(),
                        "درخواست پر عمل نہیں ہو سکا"));
        }
}
