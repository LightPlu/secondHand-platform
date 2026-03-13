package com.example.auction.domain.bid.repository;

import com.example.auction.domain.bid.entity.Bid;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BidRepository extends JpaRepository<Bid, Long> {

    // 경매별 입찰 목록 (최고가순)
    List<Bid> findByAuctionIdOrderByBidPriceDesc(Long auctionId);

    // 경매별 최고 입찰가 조회
    Optional<Bid> findTopByAuctionIdOrderByBidPriceDesc(Long auctionId);

    // 사용자별 입찰 목록 (최신순)
    List<Bid> findByUserIdOrderByBidTimeDesc(Long userId);

    // 경매별 입찰 수
    long countByAuctionId(Long auctionId);
}

