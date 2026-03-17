package com.example.auction.domain.auction.entity;

import com.example.auction.domain.auction.enums.AuctionStatus;
import com.example.auction.domain.product.entity.Product;
import com.example.auction.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "auctions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Auction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private Product product;

    @Column(nullable = false)
    private Long startPrice;

    @Column(nullable = false)
    private Long currentPrice;

    @Column(nullable = false)
    private LocalDateTime startTime;

    @Column(nullable = false)
    private LocalDateTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuctionStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "winner_id")
    private User winner;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // 현재 입찰가 갱신
    public void updateCurrentPrice(Long price) {
        this.currentPrice = price;
    }

    // 경매 상태 변경
    public void changeStatus(AuctionStatus status) {
        this.status = status;
    }

    // 낙찰자 지정
    public void assignWinner(User winner) {
        this.winner = winner;
    }

    // 경매 시작 가능 여부
    public boolean isStartable() {
        return this.status == AuctionStatus.READY
                && LocalDateTime.now().isAfter(this.startTime);
    }

    // 경매 종료 여부
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.endTime);
    }
}
