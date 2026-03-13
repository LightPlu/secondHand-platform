package com.example.auction.domain.bid.dto;

import com.example.auction.domain.bid.entity.Bid;
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
@Schema(description = "입찰 응답")
public class BidResponse {

    @Schema(description = "입찰 ID", example = "1")
    private Long id;

    @Schema(description = "경매 ID", example = "1")
    private Long auctionId;

    @Schema(description = "입찰자 ID", example = "1")
    private Long userId;

    @Schema(description = "입찰자 닉네임", example = "입찰왕")
    private String userNickname;

    @Schema(description = "입찰 금액", example = "150000")
    private Long bidPrice;

    @Schema(description = "입찰 시간")
    private LocalDateTime bidTime;

    public static BidResponse from(Bid bid) {
        return BidResponse.builder()
                .id(bid.getId())
                .auctionId(bid.getAuction().getId())
                .userId(bid.getUser().getId())
                .userNickname(bid.getUser().getNickname())
                .bidPrice(bid.getBidPrice())
                .bidTime(bid.getBidTime())
                .build();
    }
}

