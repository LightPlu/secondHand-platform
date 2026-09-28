package com.example.auction.domain.bid.service;

import com.example.auction.domain.auction.entity.Auction;
import com.example.auction.domain.auction.enums.AuctionStatus;
import com.example.auction.domain.auction.repository.AuctionRepository;
import com.example.auction.domain.bid.dto.BidQueueMessage;
import com.example.auction.domain.bid.dto.BidRequest;
import com.example.auction.domain.bid.entity.Bid;
import com.example.auction.domain.bid.repository.BidRepository;
import com.example.auction.domain.product.entity.Product;
import com.example.auction.domain.product.enums.ProductStatus;
import com.example.auction.domain.product.repository.ProductRepository;
import com.example.auction.domain.user.entity.User;
import com.example.auction.domain.user.enums.UserRole;
import com.example.auction.domain.user.enums.UserStatus;
import com.example.auction.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("local")
@DisplayName("Redis 큐 통합 테스트: 동시 입찰 처리")
class BidQueueIntegrationSimpleTest {

    @Autowired
    private BidQueueService bidQueueService;

    @Autowired
    private BidService bidService;

    @Autowired
    private BidRepository bidRepository;

    @Autowired
    private AuctionRepository auctionRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    private Auction testAuction;
    private List<User> bidders;
    private static final int BIDDER_COUNT = 100;
    private static final long INITIAL_PRICE = 10_000L;

    @BeforeEach
    @Transactional
    void setUp() {
        // 큐 초기화
        bidQueueService.clearQueue();

        // email 고유성용 전체 ID + nickname 길이 제한(20) 대응용 짧은 suffix
        String uniqueId = String.valueOf(System.currentTimeMillis());
        String nickSuffix = uniqueId.substring(Math.max(0, uniqueId.length() - 8));

        // 테스트용 판매자 생성
        User seller = userRepository.save(User.builder()
                .email("seller_" + uniqueId + "@test.com")
                .password("password")
                .name("판매자")
                .nickname("s_" + nickSuffix)
                .phoneNumber("010-0000-0000")
                .address("서울")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build());

        // 테스트용 상품 생성
        Product product = productRepository.save(Product.builder()
                .seller(seller)
                .title("테스트_" + uniqueId)
                .description("테스트용 상품")
                .category("전자기기")
                .buyNowPrice(INITIAL_PRICE).currentPrice(INITIAL_PRICE)
                .status(ProductStatus.AUCTION)
                .build());

        // 테스트용 경매 생성
        testAuction = auctionRepository.save(Auction.builder()
                .product(product)
                .buyNowPrice(INITIAL_PRICE)
                .currentPrice(INITIAL_PRICE)
                .startTime(LocalDateTime.now().minusHours(1))
                .endTime(LocalDateTime.now().plusHours(2))
                .status(AuctionStatus.RUNNING)
                .build());

        // 100명의 입찰자 생성
        bidders = new ArrayList<>();
        for (int i = 1; i <= BIDDER_COUNT; i++) {
            User bidder = userRepository.save(User.builder()
                    .email("bidder_" + uniqueId + "_" + i + "@test.com")
                    .password("password")
                    .name("입찰자" + i)
                    .nickname("b_" + nickSuffix + "_" + i)
                    .phoneNumber("010-" + String.format("%04d", i) + "-0000")
                    .address("서울")
                    .role(UserRole.USER)
                    .status(UserStatus.ACTIVE)
                    .build());
            bidders.add(bidder);
        }
    }

    @Test
    @DisplayName("[통합테스트] 100명 동시 입찰 → 큐 처리 → DB 검증")
    @Transactional
    void testBidQueueEnd2End() throws InterruptedException {
        int totalBidders = BIDDER_COUNT;
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch enqueueLatch = new CountDownLatch(totalBidders);
        AtomicInteger successEnqueue = new AtomicInteger(0);

        System.out.println("\n" + "=".repeat(60));
        System.out.println("🔍 Redis 큐 통합 테스트: 100명 동시 입찰");
        System.out.println("=".repeat(60));

        // 1단계: Enqueue
        System.out.println("\n[1단계] 100명이 동시에 Redis 큐에 입찰 요청 추가");
        long enqueueStart = System.currentTimeMillis();

        for (int i = 0; i < totalBidders; i++) {
            final int bidderIndex = i;
            executor.submit(() -> {
                try {
                    User bidder = bidders.get(bidderIndex);
                    long bidPrice = INITIAL_PRICE + (1_000L * (bidderIndex + 1));

                    BidQueueMessage message = BidQueueMessage.builder()
                            .auctionId(testAuction.getId())
                            .bidderId(bidder.getEmail())
                            .bidPrice(bidPrice)
                            .enqueuedAt(LocalDateTime.now())
                            .build();

                    bidQueueService.enqueueBid(message);
                    successEnqueue.incrementAndGet();
                } finally {
                    enqueueLatch.countDown();
                }
            });
        }

        enqueueLatch.await();
        long enqueueTime = System.currentTimeMillis() - enqueueStart;
        long queueSize = bidQueueService.getQueueSize();

        System.out.println("  ✓ Enqueue 완료");
        System.out.println("    - 성공: " + successEnqueue.get() + "건");
        System.out.println("    - 소요시간: " + enqueueTime + "ms");
        System.out.println("    - 큐 크기: " + queueSize);

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // 검증 1
        assertEquals(totalBidders, successEnqueue.get(), "모든 요청이 Enqueue되어야 함");
        assertEquals(totalBidders, queueSize, "큐 크기가 100이어야 함");

        // 2단계: Worker 처리
        System.out.println("\n[2단계] Worker가 큐의 모든 요청을 순차 처리");
        long processingStart = System.currentTimeMillis();
        int processedCount = 0;

        BidQueueMessage message;
        while ((message = bidQueueService.dequeueBid()) != null) {
            try {
                BidRequest bidRequest = new BidRequest(message.getBidPrice());
                bidService.placeBidFromQueue(message.getBidderId(), message.getAuctionId(), bidRequest);
                processedCount++;
            } catch (Exception e) {
                // 입찰가 검증 실패 (정상, 첫번째만 성공)
            }
        }

        long processingTime = System.currentTimeMillis() - processingStart;
        long finalQueueSize = bidQueueService.getQueueSize();

        System.out.println("  ✓ 처리 완료");
        System.out.println("    - 성공: " + processedCount + "건");
        System.out.println("    - 소요시간: " + processingTime + "ms");
        System.out.println("    - 남은 큐: " + finalQueueSize);

        // 검증 2
        assertEquals(0, finalQueueSize, "큐가 비어있어야 함");

        // 3단계: DB 검증
        System.out.println("\n[3단계] DB 저장 검증 - Lost Update 확인");

        // 경매의 현재가 확인
        Auction updatedAuction = auctionRepository.findById(testAuction.getId()).orElseThrow();
        System.out.println("  - 경매 현재가: " + updatedAuction.getCurrentPrice() + "원");

        // 입찰 목록 조회
        List<Bid> allBids = bidRepository.findByAuctionIdOrderByBidPriceDesc(testAuction.getId());
        System.out.println("  - 저장된 입찰: " + allBids.size() + "건");

        // 고유한 입찰가 확인 (Lost Update 없는지 확인)
        Set<Long> uniquePrices = allBids.stream()
                .map(Bid::getBidPrice)
                .collect(Collectors.toSet());
        System.out.println("  - 고유 입찰가: " + uniquePrices.size() + "개");

        long maxBidPrice = allBids.isEmpty() ? 0 : allBids.get(0).getBidPrice();
        long expectedMaxPrice = INITIAL_PRICE + (1_000L * totalBidders);

        System.out.println("  - 최고 입찰가: " + maxBidPrice + "원 (예상: " + expectedMaxPrice + "원)");

        // 최종 검증
        System.out.println("\n" + "=".repeat(60));
        System.out.println("✅ 검증 결과");
        System.out.println("=".repeat(60));

        assertEquals(totalBidders, allBids.size(), "모든 입찰이 저장되어야 함");
        assertEquals(totalBidders, uniquePrices.size(), "[중요] 중복 없음 - Lost Update 미발생! ✓");
        assertEquals(expectedMaxPrice, maxBidPrice, "최고가가 정확히 저장됨");
        assertEquals(expectedMaxPrice, updatedAuction.getCurrentPrice(), "경매 현재가 = 최고 입찰가");

        System.out.println("✓ 모든 요청 제대로 처리됨: " + allBids.size() + "/" + totalBidders);
        System.out.println("✓ Lost Update 문제 없음");
        System.out.println("✓ 최고가 정확히 저장됨: " + maxBidPrice + "원");
        System.out.println("=".repeat(60) + "\n");
    }
}
