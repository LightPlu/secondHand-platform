package com.example.auction.domain.bid.service;

import com.example.auction.domain.auction.entity.Auction;
import com.example.auction.domain.auction.enums.AuctionStatus;
import com.example.auction.domain.auction.repository.AuctionRepository;
import com.example.auction.domain.bid.dto.BidRequest;
import com.example.auction.domain.bid.dto.BidResponse;
import com.example.auction.domain.bid.entity.Bid;
import com.example.auction.domain.bid.repository.BidRepository;
import com.example.auction.domain.user.entity.User;
import com.example.auction.domain.user.repository.UserRepository;
import com.example.auction.global.exception.AuctionNotFoundException;
import com.example.auction.global.lock.annotation.ReentrantAuctionLock;
import com.example.auction.global.lock.annotation.SynchronizedAuctionLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BidService {

    private final BidRepository bidRepository;
    private final AuctionRepository auctionRepository;
    private final UserRepository userRepository;

    // 입찰(ReentrantLock 전략)
    @Transactional
    @ReentrantAuctionLock(keyArgIndex = 1, keyPrefix = "bid:place", timeoutMillis = 3000)
    public BidResponse placeBid(String email, Long auctionId, BidRequest request) {
        return doPlaceBid(email, auctionId, request);
    }

    // 입찰(synchronized 전략)
    @Transactional
    @SynchronizedAuctionLock(keyArgIndex = 1, keyPrefix = "bid:place")
    public BidResponse placeBidWithSynchronizedLock(String email, Long auctionId, BidRequest request) {
        return doPlaceBid(email, auctionId, request);
    }

    private BidResponse doPlaceBid(String email, Long auctionId, BidRequest request) {
        User bidder = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));

        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new AuctionNotFoundException("경매를 찾을 수 없습니다: " + auctionId));

        // 경매 진행 중 여부 확인
        if (auction.getStatus() != AuctionStatus.RUNNING) {
            throw new RuntimeException("진행 중인 경매에만 입찰할 수 있습니다. 현재 상태: " + auction.getStatus());
        }

        // 경매 종료 시간 확인
        if (auction.isExpired()) {
            throw new RuntimeException("종료된 경매입니다.");
        }

        // 본인 상품 입찰 불가
        if (auction.getProduct().getSeller().getEmail().equals(email)) {
            throw new RuntimeException("본인이 등록한 상품에는 입찰할 수 없습니다.");
        }

        // 현재 최고 입찰가보다 높아야 함
        if (request.getBidPrice() <= auction.getCurrentPrice()) {
            throw new RuntimeException(
                    "입찰가는 현재 최고 입찰가(" + auction.getCurrentPrice() + "원)보다 높아야 합니다."
            );
        }

        // 입찰 저장
        Bid bid = Bid.builder()
                .auction(auction)
                .user(bidder)
                .bidPrice(request.getBidPrice())
                .build();

        bidRepository.save(bid);

        // 경매 현재가 갱신
        auction.updateCurrentPrice(request.getBidPrice());

        log.info("입찰 완료: auctionId={}, userId={}, bidPrice={}",
                auctionId, bidder.getId(), request.getBidPrice());

        return BidResponse.from(bid);
    }

    // 경매별 입찰 목록 조회
    public List<BidResponse> getBidsByAuction(Long auctionId) {
        if (!auctionRepository.existsById(auctionId)) {
            throw new AuctionNotFoundException("경매를 찾을 수 없습니다: " + auctionId);
        }
        return bidRepository.findByAuctionIdOrderByBidPriceDesc(auctionId)
                .stream()
                .map(BidResponse::from)
                .collect(Collectors.toList());
    }

    // 내 입찰 목록 조회
    public List<BidResponse> getMyBids(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));
        return bidRepository.findByUserIdOrderByBidTimeDesc(user.getId())
                .stream()
                .map(BidResponse::from)
                .collect(Collectors.toList());
    }

    // 경매 최고 입찰 조회
    public BidResponse getHighestBid(Long auctionId) {
        return bidRepository.findTopByAuctionIdOrderByBidPriceDesc(auctionId)
                .map(BidResponse::from)
                .orElseThrow(() -> new RuntimeException("아직 입찰 내역이 없습니다."));
    }

    // 경매별 입찰 수 조회
    public long getBidCount(Long auctionId) {
        return bidRepository.countByAuctionId(auctionId);
    }
}
