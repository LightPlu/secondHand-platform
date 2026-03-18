package com.example.auction.domain.bid.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "입찰 요청")
public class BidRequest {

    @NotNull(message = "입찰가는 필수입니다.")
    @Min(value = 1, message = "입찰가는 1원 이상이어야 합니다.")
    @Schema(description = "입찰 금액", example = "150000")
    private Long bidPrice;

    public BidRequest(Long bidPrice) {
        this.bidPrice = bidPrice;
    }
}

