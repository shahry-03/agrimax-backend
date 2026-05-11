package com.agrimax.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Standardized API response wrapper used across all AgriMax endpoints.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private boolean success;
    private String messageEn;
    private String messageUr;
    private T data;
    private String error;

    @Builder.Default
    private Instant timestamp = Instant.now();

    public static <T> ApiResponse<T> success(T data, String messageEn, String messageUr) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .messageEn(messageEn)
                .messageUr(messageUr)
                .build();
    }

    public static <T> ApiResponse<T> success(String messageEn, String messageUr) {
        return ApiResponse.<T>builder()
                .success(true)
                .messageEn(messageEn)
                .messageUr(messageUr)
                .build();
    }

    public static <T> ApiResponse<T> error(String error, String messageEn, String messageUr) {
        return ApiResponse.<T>builder()
                .success(false)
                .error(error)
                .messageEn(messageEn)
                .messageUr(messageUr)
                .build();
    }
}
