package com.example.auction.domain.bid.service;

import com.example.auction.domain.auction.entity.Auction;
import com.example.auction.domain.auction.enums.AuctionStatus;
import com.example.auction.domain.auction.repository.AuctionRepository;
import com.example.auction.domain.bid.dto.BidQueueMessage;
import com.example.auction.domain.bid.dto.BidRequest;
import com.example.auction.domain.bid.repository.BidRepository;
import com.example.auction.domain.product.entity.Product;
import com.example.auction.domain.product.enums.ProductStatus;
import com.example.auction.domain.product.repository.ProductRepository;
import com.example.auction.domain.user.entity.User;
import com.example.auction.domain.user.enums.UserRole;
import com.example.auction.domain.user.enums.UserStatus;
import com.example.auction.domain.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Tag("benchmark")
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.show_sql=false",
        "spring.jpa.properties.hibernate.format_sql=false",
        "logging.level.org.hibernate=warn",
        "logging.level.org.hibernate.SQL=off",
        "logging.level.org.hibernate.orm.jdbc.bind=off"
})
@DisplayName("입찰 전략 벤치마크 통합 테스트 템플릿")
class BidStrategyBenchmarkTemplateIT {

    private static final int BIDDER_COUNT = Integer.getInteger("benchmark.bidders", 100);
    private static final int WARMUP_ROUNDS = Integer.getInteger("benchmark.warmupRounds", 2);
    private static final int MEASURE_ROUNDS = Integer.getInteger("benchmark.measureRounds", 5);
    private static final int THREAD_POOL_SIZE = Integer.getInteger("benchmark.threadPoolSize", 20);

    private static final long INITIAL_PRICE = 10_000L;
    private static final long BID_INCREMENT = 1_000L;

    // 전체 테스트 실행에서 닉네임/이메일 유일성을 보장하기 위한 전역 시퀀스
    // (nickname 컬럼 unique length=20, email 컬럼 length=50 제약 회피)
    private static final AtomicInteger NICKNAME_SEQUENCE = new AtomicInteger(0);
    private static final AtomicInteger EMAIL_SEQUENCE = new AtomicInteger(0);

    @Autowired
    private BidService bidService;

    @Autowired
    private BidQueueService bidQueueService;

    // 실제 @Scheduled 워커가 벤치마크용 Redis 큐를 동시 소비하지 못하도록 무력화
    // (측정은 테스트가 직접 드레인하며 순차 처리 성능을 잰다)
    @MockitoBean
    private BidQueueWorker bidQueueWorker;

    @Autowired
    private BidRepository bidRepository;

    @Autowired
    private AuctionRepository auctionRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void tearDown() {
        bidQueueService.clearQueue();
        bidRepository.deleteAllInBatch();
        auctionRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("템플릿: Reentrant/Synchronized/RedisQueue 전략 측정")
    void benchmarkBidStrategiesTemplate() {
        printConfig();
        runScenario(Strategy.REENTRANT_LOCK);
        runScenario(Strategy.SYNCHRONIZED_LOCK);
        runScenario(Strategy.REDIS_QUEUE);
    }

    private void runScenario(Strategy strategy) {
        List<Long> elapsedMsList = new ArrayList<>();
        List<RoundOutcome> outcomes = new ArrayList<>();

        for (int round = 1; round <= WARMUP_ROUNDS; round++) {
            int currentRound = round;
            assertTimeoutPreemptively(Duration.ofSeconds(60), () -> executeRound(strategy, currentRound, true));
        }

        for (int round = 1; round <= MEASURE_ROUNDS; round++) {
            int currentRound = round;
            BenchmarkRoundResult result = assertTimeoutPreemptively(
                    Duration.ofSeconds(60),
                    () -> executeRound(strategy, currentRound, false)
            );
            elapsedMsList.add(result.elapsedMs());
            outcomes.add(result.outcome());
        }

        printSummary(strategy, elapsedMsList, outcomes);
    }

    private BenchmarkRoundResult executeRound(Strategy strategy, int round, boolean warmup) throws InterruptedException {
        TestFixture fixture = prepareFixture(strategy, round, warmup);

        long startNs = System.nanoTime();
        RoundOutcome outcome;
        if (strategy == Strategy.REDIS_QUEUE) {
            outcome = executeRedisQueuePath(fixture);
        } else {
            outcome = executeLockPath(fixture, strategy);
        }
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);

        long expectedMaxPrice = INITIAL_PRICE + BID_INCREMENT * BIDDER_COUNT;
        Auction updatedAuction = auctionRepository.findById(fixture.auctionId()).orElseThrow();

        assertEquals(BIDDER_COUNT, outcome.successCount() + outcome.failCount(), "모든 요청은 처리되어야 합니다.");
        assertEquals(expectedMaxPrice, updatedAuction.getCurrentPrice(), "최종 현재가는 최고 입찰가여야 합니다.");

        if (strategy == Strategy.REDIS_QUEUE) {
            assertEquals(0, bidQueueService.getQueueSize(), "큐는 모두 비워져야 합니다.");
        }

        printResult(strategy, round, warmup, elapsedMs, outcome, updatedAuction.getCurrentPrice());
        return new BenchmarkRoundResult(elapsedMs, outcome);
    }

    private RoundOutcome executeLockPath(TestFixture fixture, Strategy strategy) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        try {
            CountDownLatch latch = new CountDownLatch(BIDDER_COUNT);
            AtomicInteger success = new AtomicInteger(0);
            AtomicInteger priceReject = new AtomicInteger(0);
            AtomicInteger lockFail = new AtomicInteger(0);
            AtomicInteger otherFail = new AtomicInteger(0);

            for (int i = 0; i < BIDDER_COUNT; i++) {
                int idx = i;
                executor.submit(() -> {
                    try {
                        String bidderEmail = fixture.bidderEmails().get(idx);
                        long bidPrice = INITIAL_PRICE + BID_INCREMENT * (idx + 1);
                        BidRequest request = new BidRequest(bidPrice);

                        if (strategy == Strategy.REENTRANT_LOCK) {
                            bidService.placeBid(bidderEmail, fixture.auctionId(), request);
                        } else {
                            bidService.placeBidWithSynchronizedLock(bidderEmail, fixture.auctionId(), request);
                        }
                        success.incrementAndGet();
                    } catch (Exception e) {
                        countFailure(e, priceReject, lockFail, otherFail);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            executor.shutdown();
            boolean terminated = executor.awaitTermination(30, TimeUnit.SECONDS);
            assertTrue(terminated, "락 기반 벤치마크 스레드가 정상 종료되어야 합니다.");
            return new RoundOutcome(success.get(), priceReject.get(), lockFail.get(), otherFail.get());
        } finally {
            executor.shutdownNow();
        }
    }

    private RoundOutcome executeRedisQueuePath(TestFixture fixture) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        AtomicInteger enqueueFail = new AtomicInteger(0);
        try {
            CountDownLatch enqueueLatch = new CountDownLatch(BIDDER_COUNT);

            for (int i = 0; i < BIDDER_COUNT; i++) {
                int idx = i;
                executor.submit(() -> {
                    try {
                        String bidderEmail = fixture.bidderEmails().get(idx);
                        long bidPrice = INITIAL_PRICE + BID_INCREMENT * (idx + 1);
                        BidQueueMessage message = BidQueueMessage.builder()
                                .auctionId(fixture.auctionId())
                                .bidderId(bidderEmail)
                                .bidPrice(bidPrice)
                                .enqueuedAt(LocalDateTime.now())
                                .build();
                        bidQueueService.enqueueBid(message);
                    } catch (Exception e) {
                        enqueueFail.incrementAndGet();
                    } finally {
                        enqueueLatch.countDown();
                    }
                });
            }

            enqueueLatch.await();
            executor.shutdown();
            boolean terminated = executor.awaitTermination(30, TimeUnit.SECONDS);
            assertTrue(terminated, "큐 Enqueue 스레드가 정상 종료되어야 합니다.");
        } finally {
            executor.shutdownNow();
        }

        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger priceReject = new AtomicInteger(0);
        AtomicInteger lockFail = new AtomicInteger(0);
        // Enqueue 단계 실패(Redis 장애 등)는 사유 불명이므로 기타로 집계
        AtomicInteger otherFail = new AtomicInteger(enqueueFail.get());
        BidQueueMessage message;
        while ((message = bidQueueService.dequeueBid()) != null) {
            try {
                bidService.placeBidFromQueue(
                        message.getBidderId(),
                        message.getAuctionId(),
                        new BidRequest(message.getBidPrice())
                );
                success.incrementAndGet();
            } catch (Exception e) {
                countFailure(e, priceReject, lockFail, otherFail);
            }
        }

        return new RoundOutcome(success.get(), priceReject.get(), lockFail.get(), otherFail.get());
    }

    /**
     * 실패 사유를 분류한다.
     * - 락 경합/타임아웃: AuctionLockAspect가 IllegalStateException으로 던짐 (요청이 아예 처리되지 못하고 유실)
     * - 가격 미달 거절: 현재가보다 낮은 입찰에 대한 규칙상 정상 거절
     * - 기타: 그 외(사용자/경매 미존재 등, 정상 시나리오에선 발생하지 않음)
     */
    private void countFailure(Exception e, AtomicInteger priceReject, AtomicInteger lockFail, AtomicInteger otherFail) {
        if (e instanceof IllegalStateException) {
            lockFail.incrementAndGet();
            return;
        }
        String msg = e.getMessage() == null ? "" : e.getMessage();
        if (msg.contains("현재 최고 입찰가")) {
            priceReject.incrementAndGet();
            return;
        }
        otherFail.incrementAndGet();
    }

    private TestFixture prepareFixture(Strategy strategy, int round, boolean warmup) {
        String runId = strategy.name().toLowerCase(Locale.ROOT)
                + "-"
                + (warmup ? "w" : "m")
                + "-"
                + round
                + "-"
                + Long.toString(System.nanoTime(), 36);

        User seller = userRepository.save(User.builder()
                .email(uniqueEmail())
                .password("password")
                .name("판매자")
                .nickname(compactNickname("s", 0, runId))
                .phoneNumber("010-0000-0000")
                .address("서울")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build());

        Product product = productRepository.save(Product.builder()
                .seller(seller)
                .title("benchmark-product-" + runId)
                .description("benchmark")
                .category("전자기기")
                .buyNowPrice(INITIAL_PRICE).currentPrice(INITIAL_PRICE)
                .status(ProductStatus.AUCTION)
                .build());

        Auction auction = auctionRepository.save(Auction.builder()
                .product(product)
                .buyNowPrice(INITIAL_PRICE)
                .currentPrice(INITIAL_PRICE)
                .startTime(LocalDateTime.now().minusHours(1))
                .endTime(LocalDateTime.now().plusHours(2))
                .status(AuctionStatus.RUNNING)
                .build());

        List<String> bidderEmails = new ArrayList<>();
        for (int i = 1; i <= BIDDER_COUNT; i++) {
            String email = uniqueEmail();
            userRepository.save(User.builder()
                    .email(email)
                    .password("password")
                    .name("입찰자" + i)
                    .nickname(compactNickname("b", i, runId))
                    .phoneNumber(String.format("010-%04d-%04d", i / 100, i % 10000))
                    .address("서울")
                    .role(UserRole.USER)
                    .status(UserStatus.ACTIVE)
                    .build());
            bidderEmails.add(email);
        }

        bidQueueService.clearQueue();
        return new TestFixture(auction.getId(), bidderEmails);
    }

    private String compactNickname(String prefix, int index, String runId) {
        // 전역 시퀀스를 base36으로 인코딩해 20자 이내 유일 닉네임 생성 (라운드 간 충돌 방지)
        return prefix + Integer.toString(NICKNAME_SEQUENCE.incrementAndGet(), 36);
    }

    private String uniqueEmail() {
        // email 컬럼(length=50) 제약을 넘지 않도록 짧고 유일한 이메일 생성
        return "u" + Integer.toString(EMAIL_SEQUENCE.incrementAndGet(), 36) + "@t.co";
    }

    private void printConfig() {
        System.out.println("BENCHMARK_CONFIG|bidders=" + BIDDER_COUNT
                + "|warmup=" + WARMUP_ROUNDS
                + "|measure=" + MEASURE_ROUNDS
                + "|threads=" + THREAD_POOL_SIZE);
    }

    private void printResult(
            Strategy strategy,
            int round,
            boolean warmup,
            long elapsedMs,
            RoundOutcome outcome,
            long finalCurrentPrice
    ) {
        double throughput = elapsedMs == 0 ? 0.0 : (double) BIDDER_COUNT * 1000.0 / elapsedMs;
        System.out.println("BENCHMARK_RESULT"
                + "|strategy=" + strategy.name()
                + "|phase=" + (warmup ? "warmup" : "measure")
                + "|round=" + round
                + "|success=" + outcome.successCount()
                + "|priceReject=" + outcome.priceRejectCount()
                + "|lockFail=" + outcome.lockFailCount()
                + "|otherFail=" + outcome.otherFailCount()
                + "|fail=" + outcome.failCount()
                + "|elapsedMs=" + elapsedMs
                + "|throughput=" + String.format(Locale.US, "%.2f", throughput)
                + "|finalCurrentPrice=" + finalCurrentPrice);
    }

    private void printSummary(Strategy strategy, List<Long> elapsedMsList, List<RoundOutcome> outcomes) {
        List<Long> sorted = elapsedMsList.stream().sorted(Comparator.naturalOrder()).toList();
        long min = sorted.get(0);
        long max = sorted.get(sorted.size() - 1);
        long p50 = percentile(sorted, 50);
        long p95 = percentile(sorted, 95);
        double avg = sorted.stream().mapToLong(Long::longValue).average().orElse(0.0);

        // 측정 라운드 전체에 걸친 실패 사유 집계 (락 경합으로 유실된 요청 vs 규칙상 정상 거절)
        int totalRequests = BIDDER_COUNT * outcomes.size();
        int totalLockFail = outcomes.stream().mapToInt(RoundOutcome::lockFailCount).sum();
        int totalPriceReject = outcomes.stream().mapToInt(RoundOutcome::priceRejectCount).sum();
        int totalOtherFail = outcomes.stream().mapToInt(RoundOutcome::otherFailCount).sum();
        int totalProcessed = totalRequests - totalLockFail - totalOtherFail; // 규칙 검증까지 도달한 요청 수
        double lockFailRate = totalRequests == 0 ? 0.0 : (double) totalLockFail * 100.0 / totalRequests;
        double processedRate = totalRequests == 0 ? 0.0 : (double) totalProcessed * 100.0 / totalRequests;

        System.out.println("BENCHMARK_SUMMARY"
                + "|strategy=" + strategy.name()
                + "|rounds=" + sorted.size()
                + "|minMs=" + min
                + "|avgMs=" + String.format(Locale.US, "%.2f", avg)
                + "|p50Ms=" + p50
                + "|p95Ms=" + p95
                + "|maxMs=" + max
                + "|totalRequests=" + totalRequests
                + "|processed=" + totalProcessed
                + "|processedRate=" + String.format(Locale.US, "%.1f", processedRate)
                + "|lockFail=" + totalLockFail
                + "|lockFailRate=" + String.format(Locale.US, "%.1f", lockFailRate)
                + "|priceReject=" + totalPriceReject
                + "|otherFail=" + totalOtherFail);
    }

    private long percentile(List<Long> sorted, int percentile) {
        if (sorted.isEmpty()) {
            return 0L;
        }
        int index = (int) Math.ceil((percentile / 100.0) * sorted.size()) - 1;
        index = Math.max(0, Math.min(index, sorted.size() - 1));
        return sorted.get(index);
    }

    private enum Strategy {
        REENTRANT_LOCK,
        SYNCHRONIZED_LOCK,
        REDIS_QUEUE
    }

    private record TestFixture(Long auctionId, List<String> bidderEmails) {
    }

    private record RoundOutcome(int successCount, int priceRejectCount, int lockFailCount, int otherFailCount) {
        int failCount() {
            return priceRejectCount + lockFailCount + otherFailCount;
        }
    }

    private record BenchmarkRoundResult(long elapsedMs, RoundOutcome outcome) {
    }
}

