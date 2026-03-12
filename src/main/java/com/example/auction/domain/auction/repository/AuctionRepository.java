package com.example.auction.domain.auction.repository;

import com.example.auction.domain.auction.entity.Auction;
import com.example.auction.domain.auction.enums.AuctionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AuctionRepository extends JpaRepository<Auction, Long> {

    Optional<Auction> findByProductId(Long productId);

    List<Auction> findByStatusOrderByCreatedAtDesc(AuctionStatus status);

    boolean existsByProductId(Long productId);
}

