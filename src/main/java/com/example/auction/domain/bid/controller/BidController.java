package com.example.auction.domain.bid.controller;

import com.example.auction.domain.bid.dto.BidRequest;
import com.example.auction.domain.bid.dto.BidResponse;
import com.example.auction.domain.bid.service.BidService;
import com.example.auction.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/auctions")
@RequiredArgsConstructor
@Tag(name = "Bid", description = "입찰 API")
public class BidController {

    private final BidService bidService;

    @Operation(summary = "입찰", description = "진행 중인 경매에 입찰합니다. 현재 최고 입찰가보다 높은 금액만 입찰 가능합니다.")
    @PostMapping("/{auctionId}/bids")
    public ResponseEntity<BidResponse> placeBid(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long auctionId,
            @Valid @RequestBody BidRequest request) {
        log.info("POST /api/auctions/{}/bids - 입찰 요청", auctionId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(bidService.placeBid(userDetails.getUsername(), auctionId, request));
    }

    @Operation(summary = "경매별 입찰 목록 조회", description = "해당 경매의 입찰 목록을 최고가 순으로 조회합니다.")
    @GetMapping("/{auctionId}/bids")
    public ResponseEntity<List<BidResponse>> getBidsByAuction(@PathVariable Long auctionId) {
        log.info("GET /api/auctions/{}/bids - 입찰 목록 조회", auctionId);
        return ResponseEntity.ok(bidService.getBidsByAuction(auctionId));
    }

    @Operation(summary = "경매 최고 입찰 조회", description = "해당 경매의 현재 최고 입찰 정보를 조회합니다.")
    @GetMapping("/{auctionId}/bids/highest")
    public ResponseEntity<BidResponse> getHighestBid(@PathVariable Long auctionId) {
        log.info("GET /api/auctions/{}/bids/highest - 최고 입찰 조회", auctionId);
        return ResponseEntity.ok(bidService.getHighestBid(auctionId));
    }

    @Operation(summary = "경매별 입찰 수 조회", description = "해당 경매의 총 입찰 수를 조회합니다.")
    @GetMapping("/{auctionId}/bids/count")
    public ResponseEntity<Long> getBidCount(@PathVariable Long auctionId) {
        log.info("GET /api/auctions/{}/bids/count - 입찰 수 조회", auctionId);
        return ResponseEntity.ok(bidService.getBidCount(auctionId));
    }

    @Operation(summary = "내 입찰 목록 조회", description = "로그인한 사용자의 전체 입찰 목록을 조회합니다.")
    @GetMapping("/bids/me")
    public ResponseEntity<List<BidResponse>> getMyBids(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.info("GET /api/auctions/bids/me - 내 입찰 목록 조회");
        return ResponseEntity.ok(bidService.getMyBids(userDetails.getUsername()));
    }
}

