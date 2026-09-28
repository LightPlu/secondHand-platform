package com.example.auction.domain.bid.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BidQueueMessage {
    private Long auctionId;
    private String bidderId;
    private Long bidPrice;
    private LocalDateTime enqueuedAt;
}

