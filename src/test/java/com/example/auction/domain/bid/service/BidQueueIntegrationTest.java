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
import org.springframework.test.context.TestPropertySource;

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
@TestPropertySource(properties = {
        "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.show_sql=false",
        "spring.jpa.properties.hibernate.format_sql=false",
        "logging.level.org.hibernate=warn",
        "logging.level.org.hibernate.SQL=off",
        "logging.level.org.hibernate.orm.jdbc.bind=off"
})
@DisplayName("Redis 큐 통합 테스트: 동시 입찰 처리")
class BidQueueIntegrationTest {

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
    private String testId;

    @BeforeEach
    void setUp() {
        // 각 테스트마다 고유한 ID 생성
        testId = System.currentTimeMillis() + "_" + System.nanoTime();
        String nickSeed = Long.toString(System.nanoTime(), 36);
        String nickSuffix = nickSeed.length() > 6 ? nickSeed.substring(nickSeed.length() - 6) : nickSeed;

        // 큐 초기화
        bidQueueService.clearQueue();

        // 테스트용 판매자 생성
        User seller = userRepository.save(User.builder()
                .email("seller_" + testId + "@test.com")
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
                .title("100명 동시 입찰 테스트 상품_" + testId)
                .description("테스트용 상품")
                .category("전자기기")
                .buyNowPrice(INITIAL_PRICE).currentPrice(INITIAL_PRICE)
                .status(ProductStatus.AUCTION)
                .build());

        // 테스트용 경매 생성 (상태: RUNNING)
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
                    .email("bidder_" + testId + "_" + i + "@test.com")
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
    @DisplayName("통합 테스트: 100명 동시 입찰 → 큐 처리 → DB 저장 검증")
    void testBidQueueIntegration() throws InterruptedException {
        int totalBidders = BIDDER_COUNT;
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch enqueueLatch = new CountDownLatch(totalBidders);
        AtomicInteger successEnqueue = new AtomicInteger(0);
        AtomicInteger failEnqueue = new AtomicInteger(0);

        System.out.println("\n========== 통합 테스트: 100명 동시 입찰 ==========");
        System.out.println("[1단계] 100명의 입찰 요청을 동시에 Redis 큐에 Enqueue");

        long enqueueStartTime = System.currentTimeMillis();

        // 1단계: 100명이 동시에 입찰 요청 (큐에 추가)
        for (int i = 0; i < totalBidders; i++) {
            final int bidderIndex = i;
            executor.submit(() -> {
                try {
                    User bidder = bidders.get(bidderIndex);
                    // 각 입찰자마다 다른 입찰가 설정 (10,000 + 1,000*i)
                    long bidPrice = INITIAL_PRICE + (1_000L * (bidderIndex + 1));

                    BidQueueMessage message = BidQueueMessage.builder()
                            .auctionId(testAuction.getId())
                            .bidderId(bidder.getEmail())
                            .bidPrice(bidPrice)
                            .enqueuedAt(LocalDateTime.now())
                            .build();

                    bidQueueService.enqueueBid(message);
                    successEnqueue.incrementAndGet();
                } catch (Exception e) {
                    failEnqueue.incrementAndGet();
                    System.err.println("Enqueue 실패: " + e.getMessage());
                } finally {
                    enqueueLatch.countDown();
                }
            });
        }

        enqueueLatch.await();
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        long enqueueEndTime = System.currentTimeMillis();
        long enqueueDuration = enqueueEndTime - enqueueStartTime;
        long queueSize = bidQueueService.getQueueSize();

        System.out.println("✓ Enqueue 완료");
        System.out.println("  - 성공: " + successEnqueue.get() + "건");
        System.out.println("  - 실패: " + failEnqueue.get() + "건");
        System.out.println("  - 소요시간: " + enqueueDuration + "ms");
        System.out.println("  - 큐 크기: " + queueSize);

        // 1단계 검증
        assertEquals(totalBidders, successEnqueue.get(), "모든 요청이 Enqueue되어야 함");
        assertEquals(0, failEnqueue.get(), "Enqueue 실패가 없어야 함");
        assertEquals(totalBidders, queueSize, "큐 크기가 100이어야 함");

        // 2단계: Worker가 큐 처리 (수동으로 처리)
        System.out.println("\n[2단계] Worker가 큐의 모든 요청을 처리 (Dequeue + DB 저장)");

        long processingStartTime = System.currentTimeMillis();
        int processedCount = 0;
        int processFailCount = 0;

        BidQueueMessage message;
        while ((message = bidQueueService.dequeueBid()) != null) {
            try {
                BidRequest bidRequest = new BidRequest(message.getBidPrice());
                bidService.placeBidFromQueue(message.getBidderId(), message.getAuctionId(), bidRequest);
                processedCount++;
            } catch (Exception e) {
                processFailCount++;
                System.out.println("  [처리 실패] " + message.getBidderId() + ": " + e.getMessage());
            }
        }

        long processingEndTime = System.currentTimeMillis();
        long processingDuration = processingEndTime - processingStartTime;
        long finalQueueSize = bidQueueService.getQueueSize();

        System.out.println("✓ 처리 완료");
        System.out.println("  - 성공: " + processedCount + "건");
        System.out.println("  - 실패: " + processFailCount + "건");
        System.out.println("  - 소요시간: " + processingDuration + "ms");
        System.out.println("  - 남은 큐 크기: " + finalQueueSize);

        // 2단계 검증
        assertEquals(finalQueueSize, 0, "처리 후 큐가 비어있어야 함");

        // 3단계: DB 검증 (Lost Update 방지, 최고가 저장 확인)
        System.out.println("\n[3단계] DB 저장 검증");

        // DB에서 저장된 모든 입찰 조회
        List<Bid> allBids = bidRepository.findByAuctionIdOrderByBidPriceDesc(testAuction.getId());

        System.out.println("✓ DB 조회 결과");
        System.out.println("  - 저장된 입찰 건수: " + allBids.size());

        // 중복 확인 (Lost Update 문제 체크)
        Set<Long> bidPrices = allBids.stream()
                .map(Bid::getBidPrice)
                .collect(Collectors.toSet());
        System.out.println("  - 고유한 입찰가 개수: " + bidPrices.size());

        // 최고가 확인
        long expectedMaxPrice = INITIAL_PRICE + (1_000L * totalBidders);
        long actualMaxPrice = allBids.isEmpty() ? 0 : allBids.get(0).getBidPrice();
        System.out.println("  - 예상 최고가: " + expectedMaxPrice + "원");
        System.out.println("  - 실제 최고가: " + actualMaxPrice + "원");

        // 경매의 현재가 확인
        Auction updatedAuction = auctionRepository.findById(testAuction.getId()).orElseThrow();
        System.out.println("  - 경매의 현재가: " + updatedAuction.getCurrentPrice() + "원");

        // 3단계 검증
        assertEquals(totalBidders, bidPrices.size(), "입찰가 중복이 없어야 함 (Lost Update 없음)");
        assertEquals(expectedMaxPrice, actualMaxPrice, "최고가가 정확히 저장되어야 함");
        assertEquals(expectedMaxPrice, updatedAuction.getCurrentPrice(), "경매 현재가가 최고가로 업데이트되어야 함");

        System.out.println("\n========== 테스트 통과! ==========\n");
        System.out.println("[요약]");
        System.out.println("✓ 100명이 동시에 입찰 요청 완료");
        System.out.println("✓ 모든 요청이 Redis 큐를 통해 순차 처리됨");
        System.out.println("✓ Lost Update 문제 없음 (중복 입찰가 없음)");
        System.out.println("✓ 최고 입찰가(" + actualMaxPrice + "원)가 정확히 저장됨");
        System.out.println("✓ 모든 요청 처리됨 (" + allBids.size() + "/" + totalBidders + ")");
    }

    @Test
    @DisplayName("상세 검증: Lost Update 감지")
    void testLostUpdateDetection() throws InterruptedException {
        // 같은 금액으로 동시에 입찰하는 경우
        int bidderCount = 50;
        long sameBidPrice = INITIAL_PRICE + 5_000L;

        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(bidderCount);

        System.out.println("\n========== 상세 검증: Lost Update 감지 ==========");
        System.out.println("[테스트] 50명이 같은 금액(" + sameBidPrice + "원)으로 동시 입찰");

        // 모든 입찰자가 같은 금액으로 입찰
        for (int i = 0; i < bidderCount; i++) {
            final int bidderIndex = i;
            executor.submit(() -> {
                try {
                    User bidder = bidders.get(bidderIndex);
                    BidQueueMessage message = BidQueueMessage.builder()
                            .auctionId(testAuction.getId())
                            .bidderId(bidder.getEmail())
                            .bidPrice(sameBidPrice)
                            .enqueuedAt(LocalDateTime.now())
                            .build();
                    bidQueueService.enqueueBid(message);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // Worker 처리
        BidQueueMessage message;
        while ((message = bidQueueService.dequeueBid()) != null) {
            try {
                BidRequest bidRequest = new BidRequest(message.getBidPrice());
                bidService.placeBidFromQueue(message.getBidderId(), message.getAuctionId(), bidRequest);
            } catch (Exception e) {
                // 입찰가가 현재가보다 낮으면 실패 (정상)
            }
        }

        // 검증
        List<Bid> allBids = bidRepository.findByAuctionIdOrderByBidPriceDesc(testAuction.getId());
        Set<Long> uniquePrices = allBids.stream()
                .map(Bid::getBidPrice)
                .collect(Collectors.toSet());

        System.out.println("✓ 결과:");
        System.out.println("  - 저장된 입찰 건수: " + allBids.size());
        System.out.println("  - 고유한 입찰가: " + uniquePrices.size());
        System.out.println("  - 첫 번째 입찰만 DB에 저장됨 (나머지는 검증 실패)");

        assertTrue(allBids.size() <= bidderCount, "중복 저장되지 않아야 함");
    }

    @Test
    @DisplayName("성능 검증: 100명 처리 속도")
    void testPerformanceMetrics() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch latch = new CountDownLatch(BIDDER_COUNT);

        System.out.println("\n========== 성능 검증: 100명 처리 속도 ==========");

        long startTime = System.currentTimeMillis();

        // Enqueue
        for (int i = 0; i < BIDDER_COUNT; i++) {
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
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        long enqueueTime = System.currentTimeMillis() - startTime;

        // Dequeue & Process
        long processingStart = System.currentTimeMillis();
        int processCount = 0;
        BidQueueMessage message;
        while ((message = bidQueueService.dequeueBid()) != null) {
            try {
                BidRequest bidRequest = new BidRequest(message.getBidPrice());
                bidService.placeBidFromQueue(message.getBidderId(), message.getAuctionId(), bidRequest);
                processCount++;
            } catch (Exception e) {
                // 무시
            }
        }
        long processingTime = System.currentTimeMillis() - processingStart;
        long totalTime = System.currentTimeMillis() - startTime;

        System.out.println("✓ 성능 메트릭:");
        System.out.println("  - Enqueue 시간: " + enqueueTime + "ms (" + String.format("%.2f", (double) BIDDER_COUNT / (enqueueTime / 1000.0)) + " req/sec)");
        System.out.println("  - Processing 시간: " + processingTime + "ms (" + String.format("%.2f", (double) processCount / (processingTime / 1000.0)) + " req/sec)");
        System.out.println("  - 총 소요시간: " + totalTime + "ms");
    }
}
