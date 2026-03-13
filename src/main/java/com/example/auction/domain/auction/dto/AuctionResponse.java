package com.example.auction.domain.auction.dto;

import com.example.auction.domain.auction.entity.Auction;
import com.example.auction.domain.auction.enums.AuctionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "경매 응답")
public class AuctionResponse {

    @Schema(description = "경매 ID", example = "1")
    private Long id;

    @Schema(description = "상품 ID", example = "1")
    private Long productId;

    @Schema(description = "상품 제목", example = "아이폰 15 팝니다")
    private String productTitle;

    @Schema(description = "판매자 닉네임", example = "판매왕")
    private String sellerNickname;

    @Schema(description = "경매 시작가", example = "100000")
    private Long startPrice;

    @Schema(description = "현재 입찰가", example = "150000")
    private Long currentPrice;

    @Schema(description = "경매 시작 시간")
    private LocalDateTime startTime;

    @Schema(description = "경매 종료 시간")
    private LocalDateTime endTime;

    @Schema(description = "경매 상태", example = "RUNNING")
    private AuctionStatus status;

    @Schema(description = "총 입찰 수", example = "5")
    private long bidCount;

    @Schema(description = "등록일시")
    private LocalDateTime createdAt;

    public static AuctionResponse from(Auction auction) {
        return AuctionResponse.builder()
                .id(auction.getId())
                .productId(auction.getProduct().getId())
                .productTitle(auction.getProduct().getTitle())
                .sellerNickname(auction.getProduct().getSeller().getNickname())
                .startPrice(auction.getStartPrice())
                .currentPrice(auction.getCurrentPrice())
                .startTime(auction.getStartTime())
                .endTime(auction.getEndTime())
                .status(auction.getStatus())
                .bidCount(0)
                .createdAt(auction.getCreatedAt())
                .build();
    }

    public static AuctionResponse of(Auction auction, long bidCount) {
        return AuctionResponse.builder()
                .id(auction.getId())
                .productId(auction.getProduct().getId())
                .productTitle(auction.getProduct().getTitle())
                .sellerNickname(auction.getProduct().getSeller().getNickname())
                .startPrice(auction.getStartPrice())
                .currentPrice(auction.getCurrentPrice())
                .startTime(auction.getStartTime())
                .endTime(auction.getEndTime())
                .status(auction.getStatus())
                .bidCount(bidCount)
                .createdAt(auction.getCreatedAt())
                .build();
    }
}

