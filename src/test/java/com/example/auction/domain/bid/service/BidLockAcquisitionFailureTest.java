package com.example.auction.domain.bid.service;

import com.example.auction.global.lock.annotation.ReentrantAuctionLock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Import(BidLockAcquisitionFailureTest.TestConfig.class)
class BidLockAcquisitionFailureTest {

    @Autowired
    private LockDrivenBidService lockDrivenBidService;

    @Test
    void 동시에_많은_입찰_중_최고가_요청도_락_획득_실패로_실패할_수_있다() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(9);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        List<String> successBidders = Collections.synchronizedList(new ArrayList<>());
        List<String> failedBidders = Collections.synchronizedList(new ArrayList<>());

        Long auctionId = 1L;

        // 먼저 낮은 금액 요청이 락을 점유하도록 해서, 뒤이어 들어오는 다수 요청이 timeout 되게 만든다.
        Future<?> lockHolderFuture = executor.submit(() -> {
            lockDrivenBidService.placeBid(auctionId, "bidder-low", 11_000L, 1_500L);
            successCount.incrementAndGet();
            successBidders.add("bidder-low");
        });

        assertTrue(lockDrivenBidService.awaitFirstLockHolder(500), "락 선점 스레드가 준비되지 않았습니다.");

        // 최고가 입찰자 + 추가 경쟁 입찰자들을 동시에 출발시킨다.
        List<BidTask> contenders = List.of(
                new BidTask("bidder-high", 20_000L),
                new BidTask("bidder-1", 11_200L),
                new BidTask("bidder-2", 11_300L),
                new BidTask("bidder-3", 11_400L),
                new BidTask("bidder-4", 11_500L),
                new BidTask("bidder-5", 11_600L),
                new BidTask("bidder-6", 11_700L),
                new BidTask("bidder-7", 11_800L),
                new BidTask("bidder-8", 11_900L)
        );

        for (BidTask contender : contenders) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    lockDrivenBidService.placeBid(auctionId, contender.bidderId(), contender.bidPrice(), 0L);
                    successCount.incrementAndGet();
                    successBidders.add(contender.bidderId());
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    failedBidders.add(contender.bidderId());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();

        try {
            lockHolderFuture.get(2, TimeUnit.SECONDS);
        } catch (ExecutionException | TimeoutException e) {
            fail("락 선점 스레드 처리 중 예외 발생: " + e.getMessage());
        }

        executor.shutdown();

        assertTrue(failedBidders.contains("bidder-high"), "최고가 입찰자가 락 획득 실패 케이스에 포함되어야 합니다.");
        assertFalse(successBidders.contains("bidder-high"), "최고가 입찰자는 성공하면 안 됩니다.");

        assertEquals(1, successCount.get(), "락을 오래 점유한 첫 요청만 성공해야 합니다.");
        assertEquals(9, failCount.get(), "경쟁 요청 9건은 모두 timeout 실패해야 합니다.");

        assertEquals(11_000L, lockDrivenBidService.getAcceptedHighestBid(),
                "최고가 요청이 실패했으므로 최종 반영 최고가는 첫 요청 금액이어야 합니다.");
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        LockDrivenBidService lockDrivenBidService() {
            return new LockDrivenBidService();
        }
    }

    static class LockDrivenBidService {

        private final CountDownLatch firstLockHolder = new CountDownLatch(1);
        private final AtomicLong acceptedHighestBid = new AtomicLong(0L);

        @ReentrantAuctionLock(
                keyArgIndex = 0,
                keyPrefix = "bid:test",
                timeoutMillis = 200,
                maxRetries = 3
        )
        public void placeBid(Long auctionId, String bidderId, Long bidPrice, Long holdMillis) {
            firstLockHolder.countDown();

            if (holdMillis > 0) {
                try {
                    Thread.sleep(holdMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("sleep interrupted", e);
                }
            }

            acceptedHighestBid.updateAndGet(current -> Math.max(current, bidPrice));
        }

        boolean awaitFirstLockHolder(long timeoutMillis) throws InterruptedException {
            return firstLockHolder.await(timeoutMillis, TimeUnit.MILLISECONDS);
        }

        long getAcceptedHighestBid() {
            return acceptedHighestBid.get();
        }
    }

    private record BidTask(String bidderId, Long bidPrice) {
    }
}
