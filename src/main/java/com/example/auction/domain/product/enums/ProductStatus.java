package com.example.auction.domain.product.enums;

public enum ProductStatus {
    SALE("일반 판매 중"),
    AUCTION("경매 진행 중"),
    SOLD("판매 완료");

    private final String description;

    ProductStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}

