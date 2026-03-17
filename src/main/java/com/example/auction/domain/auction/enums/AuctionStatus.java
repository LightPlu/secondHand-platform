package com.example.auction.domain.auction.enums;

public enum AuctionStatus {
    READY("경매 대기"),
    RUNNING("경매 진행 중"),
    FINISHED("경매 종료"),
    FAILED("유찰"),
    CANCELLED("경매 취소");

    private final String description;

    AuctionStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
