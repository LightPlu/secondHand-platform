package com.example.auction.domain.auction.controller;

import com.example.auction.domain.auction.dto.AuctionResponse;
import com.example.auction.domain.auction.enums.AuctionStatus;
import com.example.auction.domain.auction.service.AuctionService;
import com.example.auction.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/auctions")
@RequiredArgsConstructor
@Tag(name = "Auction", description = "경매 조회 / 취소 API")
public class AuctionController {

    private final AuctionService auctionService;

    @Operation(summary = "경매 단건 조회", description = "경매 ID로 경매 정보를 조회합니다.")
    @GetMapping("/{auctionId}")
    public ResponseEntity<AuctionResponse> getAuction(@PathVariable Long auctionId) {
        log.info("GET /api/auctions/{} - 경매 조회", auctionId);
        return ResponseEntity.ok(auctionService.getAuction(auctionId));
    }

    @Operation(summary = "상품으로 경매 조회", description = "상품 ID로 해당 상품의 경매 정보를 조회합니다.")
    @GetMapping("/product/{productId}")
    public ResponseEntity<AuctionResponse> getAuctionByProduct(@PathVariable Long productId) {
        log.info("GET /api/auctions/product/{} - 상품별 경매 조회", productId);
        return ResponseEntity.ok(auctionService.getAuctionByProductId(productId));
    }

    @Operation(summary = "상태별 경매 목록 조회", description = "READY / RUNNING / FINISHED / CANCELLED 상태별 경매 목록을 조회합니다.")
    @GetMapping("/status/{status}")
    public ResponseEntity<List<AuctionResponse>> getAuctionsByStatus(@PathVariable AuctionStatus status) {
        log.info("GET /api/auctions/status/{} - 상태별 경매 목록 조회", status);
        return ResponseEntity.ok(auctionService.getAuctionsByStatus(status));
    }

    @Operation(summary = "경매 취소", description = "대기 중인(READY) 경매를 취소합니다. 판매자 본인만 가능합니다.")
    @PatchMapping("/{auctionId}/cancel")
    public ResponseEntity<AuctionResponse> cancelAuction(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long auctionId) {
        log.info("PATCH /api/auctions/{}/cancel - 경매 취소 요청", auctionId);
        return ResponseEntity.ok(auctionService.cancelAuction(userDetails.getUsername(), auctionId));
    }

    @Operation(summary = "경매 상태 동기화", description = "경매 시작/종료 시간에 맞게 상태를 갱신합니다.")
    @PatchMapping("/{auctionId}/sync")
    public ResponseEntity<Void> syncAuctionStatus(@PathVariable Long auctionId) {
        log.info("PATCH /api/auctions/{}/sync - 경매 상태 동기화", auctionId);
        auctionService.syncAuctionStatus(auctionId);
        return ResponseEntity.ok().build();
    }
}

