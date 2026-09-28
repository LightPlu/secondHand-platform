package com.example.auction.domain.bid.service;

import com.example.auction.global.lock.annotation.ReentrantAuctionLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ReentrantLock 방식의 락 타임아웃 실패율을 동시 요청 수에 따라 수치로 측정한다.
 *
 * <p>측정 조건:
 * <ul>
 *   <li>DB 처리 시뮬레이션: 락 보유 시간 20ms (HOLD_MS)</li>
 *   <li>tryLock 대기 시간: 1회 100ms × 최대 3회 재시도 = 총 300ms</li>
 *   <li>이론적 최대 처리 가능 수: 300ms / 20ms = 15명</li>
 * </ul>
 *
 * <p>가격 검증 실패와 완전히 분리하기 위해 비즈니스 로직이 없는
 * ScalableLockService를 사용한다.
 */
@SpringBootTest
@Import(BidLockTimeoutScalingTest.TestConfig.class)
class BidLockTimeoutScalingTest {

    // tryLock(100ms) × maxRetries(3) = 최대 300ms 대기
    // HOLD_MS=20ms 이면 이론적 최대 처리량: 300/20 = 15명
    private static final long HOLD_MS = 20L;
    private static final int THEORETICAL_MAX = 15;

    @Autowired
    private ScalableLockService scalableLockService;

    @Test
    @DisplayName("동시 입찰자 수 증가에 따른 ReentrantLock 타임아웃 실패율")
    void reentrantLock_lockTimeoutFailureRate_scalingWithLoad() throws InterruptedException {
        int[] loads = {5, 10, 20, 50, 100};

        System.out.println("\n" + "=".repeat(70));
        System.out.println("ReentrantLock 타임아웃 실패율 측정");
        System.out.printf("  - DB 처리 시간(HOLD)  : %dms%n", HOLD_MS);
        System.out.printf("  - tryLock 대기        : 100ms × 3회 = 총 300ms%n");
        System.out.printf("  - 이론적 최대 처리량   : %d명 (300ms / %dms)%n", THEORETICAL_MAX, HOLD_MS);
        System.out.println("=".repeat(70));
        System.out.printf("%-10s  %-8s  %-12s  %-12s%n", "동시요청", "성공", "락타임아웃", "타임아웃실패율");
        System.out.println("-".repeat(70));

        for (int load : loads) {
            LoadResult result = runLoadTest(load);

            System.out.printf("%-10d  %-8d  %-12d  %.1f%%%n",
                    load,
                    result.successCount(),
                    result.lockTimeoutCount(),
                    result.lockTimeoutRate());

            assertEquals(load, result.successCount() + result.lockTimeoutCount(),
                    "모든 요청은 성공 또는 락 타임아웃으로 처리되어야 합니다.");

            if (load > THEORETICAL_MAX) {
                assertTrue(result.lockTimeoutCount() > 0,
                        String.format("동시 요청(%d명)이 이론적 최대(%d명)를 초과하면 타임아웃이 발생해야 합니다.",
                                load, THEORETICAL_MAX));
            }
        }

        System.out.println("=".repeat(70));
        System.out.println("[해석] 동시 요청이 이론적 최대치(" + THEORETICAL_MAX
                + "명)를 초과하면 타임아웃 실패율이 급격히 상승합니다.");
        System.out.println("[비교] Redis 큐 방식은 Enqueue 단계에서 100%가 성공합니다.");
    }

    @Test
    @DisplayName("락 보유 시간이 길어질수록 동일 부하에서의 실패율이 증가한다")
    void reentrantLock_lockTimeoutFailureRate_scalingWithHoldTime() throws InterruptedException {
        // 동시 20명 고정, DB 처리 시간만 변화
        int fixedLoad = 20;

        System.out.println("\n" + "=".repeat(70));
        System.out.printf("ReentrantLock 타임아웃 실패율 — 동시요청 %d명 고정, DB처리시간 변화%n", fixedLoad);
        System.out.printf("  - tryLock 대기: 100ms × 3회 = 총 300ms%n");
        System.out.println("=".repeat(70));
        System.out.printf("%-12s  %-14s  %-8s  %-12s  %-12s%n",
                "DB처리시간", "이론적최대처리량", "성공", "락타임아웃", "타임아웃실패율");
        System.out.println("-".repeat(70));

        long[] holdTimes = {5L, 10L, 20L, 30L, 60L};

        for (long holdMs : holdTimes) {
            LoadResult result = runLoadTestWithHold(fixedLoad, holdMs);
            int theoreticalMax = (int) (300L / holdMs);

            System.out.printf("%-12d  %-14d  %-8d  %-12d  %.1f%%%n",
                    holdMs,
                    theoreticalMax,
                    result.successCount(),
                    result.lockTimeoutCount(),
                    result.lockTimeoutRate());
        }

        System.out.println("=".repeat(70));
        System.out.println("[해석] DB 처리가 느릴수록(holdMs ↑) 같은 부하에서 타임아웃이 더 많이 발생합니다.");
    }

    private LoadResult runLoadTest(int load) throws InterruptedException {
        return runLoadTestWithHold(load, HOLD_MS);
    }

    private LoadResult runLoadTestWithHold(int load, long holdMs) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(load);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(load);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger lockTimeoutCount = new AtomicInteger();

        for (int i = 0; i < load; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    scalableLockService.execute(1L, holdMs);
                    successCount.incrementAndGet();
                } catch (IllegalStateException e) {
                    // AuctionLockAspect가 타임아웃 시 던지는 예외
                    lockTimeoutCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        return new LoadResult(successCount.get(), lockTimeoutCount.get(), load);
    }

    record LoadResult(int successCount, int lockTimeoutCount, int total) {
        double lockTimeoutRate() {
            return 100.0 * lockTimeoutCount / total;
        }
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        ScalableLockService scalableLockService() {
            return new ScalableLockService();
        }
    }

    /**
     * 비즈니스 로직(가격 검증 등) 없이 락 경합만 시뮬레이션하는 서비스.
     * holdMs만큼 락을 보유하여 실제 DB I/O를 흉내낸다.
     */
    static class ScalableLockService {

        @ReentrantAuctionLock(
                keyArgIndex = 0,
                keyPrefix = "bid:scale-test",
                timeoutMillis = 100L,
                maxRetries = 3
        )
        public void execute(Long auctionId, long holdMs) {
            try {
                Thread.sleep(holdMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
