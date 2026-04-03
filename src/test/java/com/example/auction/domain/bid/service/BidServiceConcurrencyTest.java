package com.example.auction.domain.bid.service;

import com.example.auction.domain.auction.entity.Auction;
import com.example.auction.domain.auction.enums.AuctionStatus;
import com.example.auction.domain.auction.repository.AuctionRepository;
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
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class BidServiceConcurrencyTest {

    private static final Logger log = LoggerFactory.getLogger(BidServiceConcurrencyTest.class);

    private static final Map<String, Long> SAME_PRICE_BID_PLAN = Map.of(
            "bidder1", 11_000L,
            "bidder2", 11_000L
    );

    private static final Map<String, Long> DIFF_PRICE_BID_PLAN = Map.of(
            "bidder1", 11_100L,
            "bidder2", 11_200L,
            "bidder3", 11_300L,
            "bidder4", 11_400L,
            "bidder5", 11_500L
    );

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

    @AfterEach
    void tearDown() {
        bidRepository.deleteAllInBatch();
        auctionRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("ReentrantLock - 동일 금액 동시 입찰은 1건만 성공한다")
    void placeBid_samePrice_concurrently_reentrantLock() throws InterruptedException {
        log.info("[TEST START] samePrice/reentrant");
        Map<String, Integer> firstStarterStats = new HashMap<>();

        for (int round = 1; round <= 10; round++) {
            log.info("\n========== [ROUND {} START] samePrice/reentrant ==========" , round);
            StartOrderResult result = runSamePriceConsistencyTest(
                    (email, auctionId, request) -> bidService.placeBid(email, auctionId, request),
                    "samePrice-reentrant",
                    round
            );
            firstStarterStats.merge(result.firstStarterLabel(), 1, Integer::sum);
            log.info("[ROUND {}] firstStarterLabel={}, firstStarterThread={}",
                    round, result.firstStarterLabel(), result.firstStarterThread());
            log.info("========== [ROUND {} END] samePrice/reentrant ==========\n", round);
        }

        log.info("[TEST SUMMARY] bidPlan(samePrice/reentrant)={}", SAME_PRICE_BID_PLAN);
        log.info("[TEST SUMMARY] samePrice/reentrant firstStarterStats={}", firstStarterStats);
        log.info("[TEST END] samePrice/reentrant");
    }

    @Test
    @DisplayName("synchronized - 동일 금액 동시 입찰은 1건만 성공한다")
    void placeBid_samePrice_concurrently_synchronizedLock() throws InterruptedException {
        log.info("[TEST START] samePrice/synchronized");
        Map<String, Integer> firstStarterStats = new HashMap<>();

        for (int round = 1; round <= 10; round++) {
            log.info("\n========== [ROUND {} START] samePrice/synchronized ==========" , round);
            StartOrderResult result = runSamePriceConsistencyTest(
                    (email, auctionId, request) -> bidService.placeBidWithSynchronizedLock(email, auctionId, request),
                    "samePrice-synchronized",
                    round
            );
            firstStarterStats.merge(result.firstStarterLabel(), 1, Integer::sum);
            log.info("[ROUND {}] firstStarterLabel={}, firstStarterThread={}",
                    round, result.firstStarterLabel(), result.firstStarterThread());
            log.info("========== [ROUND {} END] samePrice/synchronized ==========\n", round);
        }

        log.info("[TEST SUMMARY] bidPlan(samePrice/synchronized)={}", SAME_PRICE_BID_PLAN);
        log.info("[TEST SUMMARY] samePrice/synchronized firstStarterStats={}", firstStarterStats);
        log.info("[TEST END] samePrice/synchronized");
    }

    @Test
    @DisplayName("ReentrantLock - 다른 금액 동시 입찰 시 최종 현재가는 최고가로 일관된다")
    void placeBid_diffPrice_concurrently_reentrantLock() throws InterruptedException {
        log.info("[TEST START] diffPrice/reentrant");
        Map<String, Integer> firstStarterStats = new HashMap<>();
        Map<Integer, Long> roundHighestPrice = new LinkedHashMap<>();

        for (int round = 1; round <= 20; round++) {
            log.info("\n========== [ROUND {} START] diffPrice/reentrant ==========" , round);
            DiffPriceRoundResult result = runDiffPriceConsistencyTest(
                    (email, auctionId, request) -> bidService.placeBid(email, auctionId, request),
                    "diffPrice-reentrant",
                    round
            );
            firstStarterStats.merge(result.startOrder().firstStarterLabel(), 1, Integer::sum);
            roundHighestPrice.put(round, result.highestPrice());
            log.info("[ROUND {}] firstStarterLabel={}, firstStarterThread={}",
                    round, result.startOrder().firstStarterLabel(), result.startOrder().firstStarterThread());
            log.info("========== [ROUND {} END] diffPrice/reentrant ==========\n", round);
        }

        log.info("[TEST SUMMARY] bidPlan(diffPrice/reentrant)={}", DIFF_PRICE_BID_PLAN);
        log.info("[TEST SUMMARY] diffPrice/reentrant firstStarterStats={}", firstStarterStats);
        roundHighestPrice.forEach((round, highestPrice) ->
                log.info("[ROUND HIGHEST PRICE] scenario=diffPrice/reentrant, round={}, highestPrice={}", round, highestPrice));
        log.info("[TEST END] diffPrice/reentrant");
    }

    @Test
    @DisplayName("synchronized - 다른 금액 동시 입찰 시 최종 현재가는 최고가로 일관된다")
    void placeBid_diffPrice_concurrently_synchronizedLock() throws InterruptedException {
        log.info("[TEST START] diffPrice/synchronized");
        Map<String, Integer> firstStarterStats = new HashMap<>();
        Map<Integer, Long> roundHighestPrice = new LinkedHashMap<>();

        for (int round = 1; round <= 20; round++) {
            log.info("\n========== [ROUND {} START] diffPrice/synchronized ==========" , round);
            DiffPriceRoundResult result = runDiffPriceConsistencyTest(
                    (email, auctionId, request) -> bidService.placeBidWithSynchronizedLock(email, auctionId, request),
                    "diffPrice-synchronized",
                    round
            );
            firstStarterStats.merge(result.startOrder().firstStarterLabel(), 1, Integer::sum);
            roundHighestPrice.put(round, result.highestPrice());
            log.info("[ROUND {}] firstStarterLabel={}, firstStarterThread={}",
                    round, result.startOrder().firstStarterLabel(), result.startOrder().firstStarterThread());
            log.info("========== [ROUND {} END] diffPrice/synchronized ==========\n", round);
        }

        log.info("[TEST SUMMARY] bidPlan(diffPrice/synchronized)={}", DIFF_PRICE_BID_PLAN);
        log.info("[TEST SUMMARY] diffPrice/synchronized firstStarterStats={}", firstStarterStats);
        roundHighestPrice.forEach((round, highestPrice) ->
                log.info("[ROUND HIGHEST PRICE] scenario=diffPrice/synchronized, round={}, highestPrice={}", round, highestPrice));
        log.info("[TEST END] diffPrice/synchronized");
    }

    private StartOrderResult runSamePriceConsistencyTest(
            BidExecutor bidExecutor,
            String scenario,
            int round
    ) throws InterruptedException {
        String suffix = shortSuffix(scenario, round);
        User seller = userRepository.save(createUser("s" + suffix + "@c.com", "s" + suffix));
        User bidder1 = userRepository.save(createUser("b1" + suffix + "@c.com", "b1" + suffix));
        User bidder2 = userRepository.save(createUser("b2" + suffix + "@c.com", "b2" + suffix));

        Product product = productRepository.save(createProduct(seller));
        Auction auction = auctionRepository.save(createAuction(product, 10_000L));

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        StartOrderResult startOrder = executeConcurrently(
                "bidder1",
                () -> executeBid(bidExecutor, bidder1.getEmail(), auction.getId(), 11_000L, successCount, failCount),
                "bidder2",
                () -> executeBid(bidExecutor, bidder2.getEmail(), auction.getId(), 11_000L, successCount, failCount)
        );

        Auction updated = auctionRepository.findById(auction.getId()).orElseThrow();
        log.info("[RESULT samePrice] round={}, auctionId={}, success={}, fail={}, bidCount={}, currentPrice={}",
                round, auction.getId(), successCount.get(), failCount.get(), bidRepository.countByAuctionId(auction.getId()), updated.getCurrentPrice());

        assertEquals(1, successCount.get(), "동일 금액 동시 입찰은 1건만 성공해야 합니다.");
        assertEquals(1, failCount.get(), "동일 금액 동시 입찰은 1건이 실패해야 합니다.");
        assertEquals(1, bidRepository.countByAuctionId(auction.getId()), "DB 입찰 건수는 1건이어야 합니다.");
        assertEquals(11_000L, updated.getCurrentPrice(), "최종 현재가는 11,000원이어야 합니다.");

        return startOrder;
    }

    private DiffPriceRoundResult runDiffPriceConsistencyTest(
            BidExecutor bidExecutor,
            String scenario,
            int round
    ) throws InterruptedException {
        String suffix = shortSuffix(scenario, round);
        User seller = userRepository.save(createUser("s" + suffix + "@c.com", "s" + suffix));
        User bidder1 = userRepository.save(createUser("b1" + suffix + "@c.com", "b1" + suffix));
        User bidder2 = userRepository.save(createUser("b2" + suffix + "@c.com", "b2" + suffix));
        User bidder3 = userRepository.save(createUser("b3" + suffix + "@c.com", "b3" + suffix));
        User bidder4 = userRepository.save(createUser("b4" + suffix + "@c.com", "b4" + suffix));
        User bidder5 = userRepository.save(createUser("b5" + suffix + "@c.com", "b5" + suffix));

        Product product = productRepository.save(createProduct(seller));
        Auction auction = auctionRepository.save(createAuction(product, 10_000L));

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        List<ConcurrentTask> tasks = new ArrayList<>();
        tasks.add(new ConcurrentTask("bidder1", () -> executeBid(bidExecutor, bidder1.getEmail(), auction.getId(), 11_100L, successCount, failCount)));
        tasks.add(new ConcurrentTask("bidder2", () -> executeBid(bidExecutor, bidder2.getEmail(), auction.getId(), 11_200L, successCount, failCount)));
        tasks.add(new ConcurrentTask("bidder3", () -> executeBid(bidExecutor, bidder3.getEmail(), auction.getId(), 11_300L, successCount, failCount)));
        tasks.add(new ConcurrentTask("bidder4", () -> executeBid(bidExecutor, bidder4.getEmail(), auction.getId(), 11_400L, successCount, failCount)));
        tasks.add(new ConcurrentTask("bidder5", () -> executeBid(bidExecutor, bidder5.getEmail(), auction.getId(), 11_500L, successCount, failCount)));

        StartOrderResult startOrder = executeConcurrently(tasks);

        Auction updated = auctionRepository.findById(auction.getId()).orElseThrow();
        long bidCount = bidRepository.countByAuctionId(auction.getId());
        long highestPrice = updated.getCurrentPrice();

        log.info("[RESULT diffPrice] round={}, auctionId={}, success={}, fail={}, bidCount={}, currentPrice={}",
                round, auction.getId(), successCount.get(), failCount.get(), bidCount, highestPrice);

        assertEquals(11_500L, highestPrice, "최종 현재가는 항상 최고가(11,500원)여야 합니다.");
        assertEquals(5, successCount.get() + failCount.get(), "요청 5건은 모두 처리되어야 합니다.");
        assertTrue(successCount.get() >= 1 && successCount.get() <= 5, "성공 건수는 1~5건 범위여야 합니다.");
        assertEquals(successCount.get(), bidCount, "성공한 입찰 건수와 DB 저장 건수는 일치해야 합니다.");

        return new DiffPriceRoundResult(startOrder, highestPrice);
    }

    private StartOrderResult executeConcurrently(
            String firstTaskLabel,
            Runnable firstTask,
            String secondTaskLabel,
            Runnable secondTask
    ) throws InterruptedException {
        List<ConcurrentTask> tasks = new ArrayList<>();
        tasks.add(new ConcurrentTask(firstTaskLabel, firstTask));
        tasks.add(new ConcurrentTask(secondTaskLabel, secondTask));
        return executeConcurrently(tasks);
    }

    private StartOrderResult executeConcurrently(List<ConcurrentTask> tasks) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(tasks.size());
        AtomicReference<String> firstStarterLabel = new AtomicReference<>("UNKNOWN");
        AtomicReference<String> firstStarterThread = new AtomicReference<>("UNKNOWN");

        log.info("[CONCURRENT] ready threadPoolSize={}", tasks.size());

        for (ConcurrentTask task : tasks) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    markFirstStarter(task.label(), firstStarterLabel, firstStarterThread);
                    task.runnable().run();
                } catch (Exception ignored) {
                    // 실제 예외 카운팅은 task 내부에서 처리
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        log.info("[CONCURRENT] start signal sent");
        doneLatch.await();
        executor.shutdown();
        log.info("[CONCURRENT] all tasks completed, firstStarterLabel={}, firstStarterThread={}",
                firstStarterLabel.get(), firstStarterThread.get());

        return new StartOrderResult(firstStarterLabel.get(), firstStarterThread.get());
    }

    private void markFirstStarter(
            String taskLabel,
            AtomicReference<String> firstStarterLabel,
            AtomicReference<String> firstStarterThread
    ) {
        if (firstStarterLabel.compareAndSet("UNKNOWN", taskLabel)) {
            firstStarterThread.set(Thread.currentThread().getName());
        }
    }

    private void executeBid(
            BidExecutor bidExecutor,
            String email,
            Long auctionId,
            long bidPrice,
            AtomicInteger successCount,
            AtomicInteger failCount
    ) {
        try {
            bidExecutor.place(email, auctionId, new BidRequest(bidPrice));
            int success = successCount.incrementAndGet();
            log.info("[BID SUCCESS] thread={}, auctionId={}, email={}, bidPrice={}, successCount={}",
                    Thread.currentThread().getName(), auctionId, email, bidPrice, success);
        } catch (Exception e) {
            int fail = failCount.incrementAndGet();
            log.warn("[BID FAIL] thread={}, auctionId={}, email={}, bidPrice={}, failCount={}, reason={}",
                    Thread.currentThread().getName(), auctionId, email, bidPrice, fail, e.getMessage());
        }
    }

    @FunctionalInterface
    private interface BidExecutor {
        void place(String email, Long auctionId, BidRequest request);
    }

    private User createUser(String email, String nickname) {
        return User.builder()
                .email(email)
                .password("encodedPassword")
                .name("테스트유저")
                .nickname(nickname)
                .phoneNumber("010-0000-0000")
                .address("서울")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
    }

    private Product createProduct(User seller) {
        return Product.builder()
                .seller(seller)
                .title("동시성 테스트 상품")
                .description("테스트 설명")
                .category("전자기기")
                .price(10_000L)
                .status(ProductStatus.AUCTION)
                .build();
    }

    private Auction createAuction(Product product, Long startPrice) {
        return Auction.builder()
                .product(product)
                .startPrice(startPrice)
                .currentPrice(startPrice)
                .startTime(LocalDateTime.now().minusMinutes(10))
                .endTime(LocalDateTime.now().plusMinutes(30))
                .status(AuctionStatus.RUNNING)
                .build();
    }

    private String shortSuffix(String scenario, int round) {
        int hash = Math.abs(scenario.hashCode() % 1000);
        int nano = (int) (System.nanoTime() % 10000);
        return String.format("%02d%03d%04d", round, hash, nano);
    }

    private record StartOrderResult(String firstStarterLabel, String firstStarterThread) {
    }

    private record ConcurrentTask(String label, Runnable runnable) {
    }

    private record DiffPriceRoundResult(StartOrderResult startOrder, long highestPrice) {
    }
}
