package com.example.auction.domain.like.controller;

import com.example.auction.domain.like.dto.LikeResponse;
import com.example.auction.domain.like.service.LikeService;
import com.example.auction.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Like", description = "상품 찜 API")
public class LikeController {

    private final LikeService likeService;

    @Operation(summary = "상품 찜", description = "상품을 찜합니다.")
    @PostMapping("/api/products/{productId}/likes")
    public ResponseEntity<LikeResponse> addLike(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long productId
    ) {
        log.info("POST /api/products/{}/likes - 찜 요청", productId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(likeService.addLike(userDetails.getUsername(), productId));
    }

    @Operation(summary = "상품 찜 취소", description = "상품 찜을 취소합니다.")
    @DeleteMapping("/api/products/{productId}/likes")
    public ResponseEntity<Void> removeLike(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long productId
    ) {
        log.info("DELETE /api/products/{}/likes - 찜 취소 요청", productId);
        likeService.removeLike(userDetails.getUsername(), productId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "상품 찜 개수 조회", description = "특정 상품의 찜 개수를 조회합니다.")
    @GetMapping("/api/products/{productId}/likes/count")
    public ResponseEntity<Map<String, Long>> getLikeCount(@PathVariable Long productId) {
        log.info("GET /api/products/{}/likes/count - 찜 개수 조회", productId);
        return ResponseEntity.ok(Map.of("count", likeService.getLikeCount(productId)));
    }

    @Operation(summary = "상품 찜 여부 조회", description = "로그인한 사용자의 특정 상품 찜 여부를 조회합니다.")
    @GetMapping("/api/likes/products/{productId}/me")
    public ResponseEntity<Map<String, Boolean>> isLiked(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long productId
    ) {
        log.info("GET /api/likes/products/{}/me - 찜 여부 조회", productId);
        boolean liked = likeService.isLiked(userDetails.getUsername(), productId);
        return ResponseEntity.ok(Map.of("liked", liked));
    }

    @Operation(summary = "내 찜 목록 조회", description = "로그인한 사용자의 찜 목록을 조회합니다.")
    @GetMapping("/api/likes/me")
    public ResponseEntity<List<LikeResponse>> getMyLikes(@AuthenticationPrincipal CustomUserDetails userDetails) {
        log.info("GET /api/likes/me - 내 찜 목록 조회");
        return ResponseEntity.ok(likeService.getMyLikes(userDetails.getUsername()));
    }
}
