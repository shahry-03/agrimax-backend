package com.agrimax.marketplace.controller;

import com.agrimax.common.dto.ApiResponse;
import com.agrimax.marketplace.entity.SellPost;
import com.agrimax.marketplace.service.SellPostService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/marketplace")
@RequiredArgsConstructor
public class SellPostController {

    private final SellPostService sellPostService;

    @GetMapping("/posts")
    public ResponseEntity<ApiResponse<List<SellPost>>> getActivePosts() {
        return ResponseEntity.ok(ApiResponse.success(
                sellPostService.getActivePosts(), "Posts loaded", "پوسٹس لوڈ ہوگئیں"));
    }

    @GetMapping("/my-posts")
    public ResponseEntity<ApiResponse<List<SellPost>>> getMyPosts(Principal principal) {
        UUID userId = UUID.fromString(principal.getName());
        return ResponseEntity.ok(ApiResponse.success(
                sellPostService.getPostsBySeller(userId), "Your posts loaded", "آپ کی پوسٹس لوڈ ہوگئیں"));
    }
}
