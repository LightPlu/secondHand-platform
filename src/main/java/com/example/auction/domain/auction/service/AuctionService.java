package com.example.auction.domain.auction.service;

import com.example.auction.domain.auction.dto.AuctionResponse;
import com.example.auction.domain.auction.entity.Auction;
import com.example.auction.domain.auction.enums.AuctionStatus;
import com.example.auction.domain.auction.repository.AuctionRepository;
import com.example.auction.domain.bid.entity.Bid;
import com.example.auction.domain.bid.repository.BidRepository;
import com.example.auction.domain.product.enums.ProductStatus;
import com.example.auction.global.exception.AuctionNotFoundException;
import com.example.auction.global.exception.InvalidAuctionStateException;
import com.example.auction.global.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuctionService {

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;

    // 경매 단건 조회
    public AuctionResponse getAuction(Long auctionId) {
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new AuctionNotFoundException("경매를 찾을 수 없습니다: " + auctionId));
        long bidCount = bidRepository.countByAuctionId(auctionId);
        return AuctionResponse.of(auction, bidCount);
    }

    // 상품으로 경매 조회
    public AuctionResponse getAuctionByProductId(Long productId) {
        Auction auction = auctionRepository.findByProductId(productId)
                .orElseThrow(() -> new AuctionNotFoundException("해당 상품의 경매를 찾을 수 없습니다."));
        long bidCount = bidRepository.countByAuctionId(auction.getId());
        return AuctionResponse.of(auction, bidCount);
    }

    // 상태별 경매 목록 조회
    public List<AuctionResponse> getAuctionsByStatus(AuctionStatus status) {
        return auctionRepository.findByStatusOrderByCreatedAtDesc(status)
                .stream()
                .map(a -> AuctionResponse.of(a, bidRepository.countByAuctionId(a.getId())))
                .collect(Collectors.toList());
    }

    // 경매 취소 (판매자만, READY 상태만)
    @Transactional
    public AuctionResponse cancelAuction(String email, Long auctionId) {
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new AuctionNotFoundException("경매를 찾을 수 없습니다: " + auctionId));

        if (!auction.getProduct().getSeller().getEmail().equals(email)) {
            throw new UnauthorizedException("경매 취소 권한이 없습니다.");
        }

        if (auction.getStatus() != AuctionStatus.READY) {
            throw new RuntimeException("대기 중인 경매만 취소할 수 있습니다.");
        }

        auction.changeStatus(AuctionStatus.CANCELLED);
        auction.getProduct().changeStatus(com.example.auction.domain.product.enums.ProductStatus.SALE);

        log.info("경매 취소 완료: auctionId={}", auctionId);
        return AuctionResponse.of(auction, 0L);
    }

    // 경매 상태 동기화 (READY → RUNNING → FINISHED/FAILED)
    @Transactional
    public void syncAuctionStatus(Long auctionId) {
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new AuctionNotFoundException("경매를 찾을 수 없습니다: " + auctionId));

        LocalDateTime now = LocalDateTime.now();

        if (auction.getStatus() == AuctionStatus.READY && now.isAfter(auction.getStartTime())) {
            auction.changeStatus(AuctionStatus.RUNNING);
            log.info("경매 시작: auctionId={}", auctionId);
        } else if (auction.getStatus() == AuctionStatus.RUNNING && now.isAfter(auction.getEndTime())) {
            bidRepository.findTopByAuctionIdOrderByBidPriceDesc(auctionId)
                    .ifPresentOrElse(
                            highestBid -> finishAuctionWithWinner(auction, highestBid),
                            () -> failAuction(auction)
                    );
        }
    }

    private void finishAuctionWithWinner(Auction auction, Bid highestBid) {
        auction.assignWinner(highestBid.getUser());
        auction.changeStatus(AuctionStatus.FINISHED);
        auction.getProduct().changeStatus(ProductStatus.SOLD);

        log.info("경매 종료(낙찰): auctionId={}, winnerId={}, bidPrice={}",
                auction.getId(), highestBid.getUser().getId(), highestBid.getBidPrice());
    }

    private void failAuction(Auction auction) {
        auction.changeStatus(AuctionStatus.FAILED);
        auction.getProduct().changeStatus(ProductStatus.SALE);

        log.info("경매 종료(유찰): auctionId={}", auction.getId());
    }

    // 경매 결과 조회 (종료된 경매만)
    public AuctionResponse getAuctionResult(Long auctionId) {
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new AuctionNotFoundException("경매를 찾을 수 없습니다: " + auctionId));

        if (auction.getStatus() != AuctionStatus.FINISHED && auction.getStatus() != AuctionStatus.FAILED) {
            throw new InvalidAuctionStateException(
                    "경매 결과는 FINISHED 또는 FAILED 상태에서만 조회할 수 있습니다. 현재 상태: " + auction.getStatus()
            );
        }

        long bidCount = bidRepository.countByAuctionId(auctionId);
        return AuctionResponse.of(auction, bidCount);
    }
}
