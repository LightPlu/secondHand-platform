package com.example.auction.domain.bid.service;

import com.example.auction.domain.bid.dto.BidQueueMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 상품(경매)별 큐 + 경매별 처리 락 구조가, 여러 인스턴스(워커)가 붙어도
 * "경매당 소비자 1명(single consumer per auction)"과 "FIFO 순차성"을 보장하는지 검증한다.
 *
 * 실제 Redis(TestContainers)를 사용하며 Spring 컨텍스트/스케줄러는 띄우지 않는다.
 * 여러 인스턴스는 각자 UUID 토큰으로 락을 잡는 워커 스레드로 시뮬레이션한다.
 */
@Testcontainers
@DisplayName("상품별 큐: 멀티 인스턴스 순차성 보장")
class BidQueuePerAuctionLockTest {

    @Container
    static GenericContainer<?> redisContainer = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private static final Long AUCTION_ID = 1L;
    private static final int MESSAGE_COUNT = 200;
    private static final int INSTANCE_COUNT = 5;
    private static final Duration LOCK_TTL = Duration.ofSeconds(10);

    private BidQueueService bidQueueService;

    @BeforeEach
    void setUp() {
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory();
        connectionFactory.setHostName(redisContainer.getHost());
        connectionFactory.setPort(redisContainer.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();

        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(connectionFactory);
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        redisTemplate.setKeySerializer(stringSerializer);
        redisTemplate.setValueSerializer(stringSerializer);
        redisTemplate.afterPropertiesSet();

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        bidQueueService = new BidQueueService(redisTemplate, objectMapper);
        bidQueueService.clearQueue();
    }

    @Test
    @DisplayName("5개 인스턴스가 동시에 드레인해도 동시 소비자는 1명이고 처리 순서는 FIFO다")
    void multipleInstances_preserveSingleConsumerAndFifoOrder() throws InterruptedException {
        // given: 한 경매에 1..N 순서대로 입찰을 넣는다 (enqueue 순서 = 기대 처리 순서)
        for (int i = 1; i <= MESSAGE_COUNT; i++) {
            bidQueueService.enqueueBid(BidQueueMessage.builder()
                    .auctionId(AUCTION_ID)
                    .bidderId("user-" + i)
                    .bidPrice((long) i)
                    .enqueuedAt(LocalDateTime.now())
                    .build());
        }
        assertEquals(MESSAGE_COUNT, bidQueueService.getQueueSize(AUCTION_ID));

        // 임계영역 동시 진입 감지기 + 처리 순서 기록
        AtomicInteger concurrentConsumers = new AtomicInteger(0);
        AtomicInteger maxConcurrentConsumers = new AtomicInteger(0);
        AtomicInteger processedCount = new AtomicInteger(0);
        List<Long> processedOrder = new CopyOnWriteArrayList<>();

        // when: 5개 인스턴스가 같은 경매 큐를 동시에 처리 시도
        ExecutorService pool = Executors.newFixedThreadPool(INSTANCE_COUNT);
        CountDownLatch done = new CountDownLatch(INSTANCE_COUNT);
        for (int instance = 0; instance < INSTANCE_COUNT; instance++) {
            pool.submit(() -> {
                try {
                    String token = UUID.randomUUID().toString();
                    while (processedCount.get() < MESSAGE_COUNT) {
                        if (!bidQueueService.tryAcquireProcessingLock(AUCTION_ID, token, LOCK_TTL)) {
                            Thread.yield();
                            continue;
                        }
                        try {
                            BidQueueMessage message;
                            while ((message = bidQueueService.dequeueBid(AUCTION_ID)) != null) {
                                // 락 보유 구간 = 임계영역. 여기서 동시 진입이 관측되면 순차성 위반.
                                int now = concurrentConsumers.incrementAndGet();
                                maxConcurrentConsumers.accumulateAndGet(now, Math::max);

                                processedOrder.add(message.getBidPrice());
                                processedCount.incrementAndGet();
                                Thread.sleep(1); // 처리 지연을 흉내내 경쟁 창을 넓힌다

                                concurrentConsumers.decrementAndGet();
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            bidQueueService.releaseProcessingLock(AUCTION_ID, token);
                        }
                    }
                } finally {
                    done.countDown();
                }
            });
        }

        assertTrue(done.await(60, TimeUnit.SECONDS), "모든 워커가 종료되어야 합니다.");
        pool.shutdownNow();

        // then
        assertEquals(MESSAGE_COUNT, processedCount.get(), "모든 메시지가 정확히 한 번 처리되어야 합니다.");
        assertEquals(1, maxConcurrentConsumers.get(),
                "동시 소비자는 항상 1명이어야 합니다(경매별 락에 의한 상호배제).");
        assertEquals(0, bidQueueService.getQueueSize(AUCTION_ID), "큐가 비어야 합니다.");

        List<Long> expectedOrder = IntStream.rangeClosed(1, MESSAGE_COUNT)
                .mapToObj(Long::valueOf)
                .collect(Collectors.toList());
        assertEquals(expectedOrder, processedOrder,
                "여러 인스턴스가 붙어도 처리 순서는 enqueue 순서(FIFO)와 같아야 합니다.");
    }
}
