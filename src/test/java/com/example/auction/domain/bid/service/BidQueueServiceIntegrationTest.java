package com.example.auction.domain.bid.service;

import com.example.auction.domain.bid.dto.BidQueueMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 실제 Redis를 사용한 BidQueueService 통합 테스트
 * TestContainers를 통해 Docker 컨테이너의 Redis를 실행하여 테스트합니다.
 *
 * 테스트 설정:
 * - @Testcontainers: TestContainers의 생명주기를 자동으로 관리
 * - Redis만 테스트하므로 Spring Boot 전체 컨텍스트 로드하지 않음
 * - 필요한 Bean만 수동으로 생성
 */
@Testcontainers
class BidQueueServiceIntegrationTest {

    @Container
    static GenericContainer<?> redisContainer = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    /**
     * TestContainers Redis 컨테이너의 연결 정보를 가져옵니다.
     */
    static String getRedisHost() {
        return redisContainer.getHost();
    }

    static int getRedisPort() {
        return redisContainer.getMappedPort(6379);
    }

    private BidQueueService bidQueueService;
    private ObjectMapper objectMapper;
    private RedisTemplate<String, Object> redisTemplate;

    @BeforeEach
    void setUp() {
        // Redis 연결 팩토리 생성
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory();
        connectionFactory.setHostName(getRedisHost());
        connectionFactory.setPort(getRedisPort());
        connectionFactory.afterPropertiesSet();

        // RedisTemplate 수동 생성
        redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(connectionFactory);
        redisTemplate.afterPropertiesSet();

        // ObjectMapper 생성
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        // BidQueueService 수동 생성
        bidQueueService = new BidQueueService(redisTemplate, objectMapper);

        // 테스트 전 Redis 초기화
        bidQueueService.clearQueue();
    }

    @Test
    @DisplayName("실제 Redis에 입찰 메시지를 추가하고 조회할 수 있다")
    void testEnqueueAndDequeueBidWithRealRedis() {
        // Arrange
        BidQueueMessage message = BidQueueMessage.builder()
                .auctionId(1L)
                .bidderId("user-1")
                .bidPrice(15000L)
                .enqueuedAt(LocalDateTime.of(2026, 3, 30, 12, 0))
                .build();

        // Act - 입찰 메시지 추가
        bidQueueService.enqueueBid(message);
        assertEquals(1, bidQueueService.getQueueSize(), "큐에 메시지가 1개 있어야 합니다");

        // Act - 입찰 메시지 꺼내기
        BidQueueMessage result = bidQueueService.dequeueBid();

        // Assert
        assertNotNull(result, "꺼낸 메시지가 null이 아니어야 합니다");
        assertEquals(message.getAuctionId(), result.getAuctionId());
        assertEquals(message.getBidderId(), result.getBidderId());
        assertEquals(message.getBidPrice(), result.getBidPrice());
        assertEquals(message.getEnqueuedAt(), result.getEnqueuedAt());
        assertEquals(0, bidQueueService.getQueueSize(), "큐가 비어있어야 합니다");
    }

    @Test
    @DisplayName("여러 입찰 메시지를 FIFO 순서로 처리할 수 있다")
    void testMultipleBidsProcessedInFifoOrder() {
        // Arrange
        List<BidQueueMessage> messages = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            messages.add(BidQueueMessage.builder()
                    .auctionId((long) i)
                    .bidderId("user-" + i)
                    .bidPrice(10000L * i)
                    .enqueuedAt(LocalDateTime.now().plusSeconds(i))
                    .build());
        }

        // Act - 모든 메시지 추가
        for (BidQueueMessage message : messages) {
            bidQueueService.enqueueBid(message);
        }
        assertEquals(5, bidQueueService.getQueueSize());

        // Act & Assert - FIFO 순서 확인
        for (int i = 0; i < 5; i++) {
            BidQueueMessage result = bidQueueService.dequeueBid();
            assertNotNull(result);
            assertEquals(messages.get(i).getAuctionId(), result.getAuctionId());
            assertEquals(messages.get(i).getBidderId(), result.getBidderId());
            assertEquals(messages.get(i).getBidPrice(), result.getBidPrice());
        }

        assertEquals(0, bidQueueService.getQueueSize(), "모든 메시지 처리 후 큐는 비어있어야 합니다");
    }

    @Test
    @DisplayName("큐에서 메시지를 꺼낼 때 null을 반환한다")
    void testDequeueBidReturnsNullWhenQueueIsEmpty() {
        // Arrange
        bidQueueService.clearQueue();

        // Act
        BidQueueMessage result = bidQueueService.dequeueBid();

        // Assert
        assertNull(result, "빈 큐에서 꺼낸 메시지는 null이어야 합니다");
    }

    @Test
    @DisplayName("큐 크기를 정확하게 조회할 수 있다")
    void testGetQueueSizeAccurately() {
        // Arrange & Act
        assertEquals(0, bidQueueService.getQueueSize(), "초기 큐 크기는 0이어야 합니다");

        // 5개의 메시지 추가
        for (int i = 1; i <= 5; i++) {
            BidQueueMessage message = BidQueueMessage.builder()
                    .auctionId((long) i)
                    .bidderId("user-" + i)
                    .bidPrice(10000L * i)
                    .enqueuedAt(LocalDateTime.now())
                    .build();
            bidQueueService.enqueueBid(message);
        }

        // Assert
        assertEquals(5, bidQueueService.getQueueSize(), "추가 후 큐 크기는 5여야 합니다");

        // 2개 꺼내기
        bidQueueService.dequeueBid();
        bidQueueService.dequeueBid();

        assertEquals(3, bidQueueService.getQueueSize(), "2개 꺼낸 후 큐 크기는 3이어야 합니다");
    }

    @Test
    @DisplayName("queueStatus를 조회할 수 있다")
    void testGetQueueStatus() {
        // Arrange
        BidQueueMessage message = BidQueueMessage.builder()
                .auctionId(1L)
                .bidderId("user-1")
                .bidPrice(15000L)
                .enqueuedAt(LocalDateTime.now())
                .build();
        bidQueueService.enqueueBid(message);

        // Act
        BidQueueService.QueueStatus status = bidQueueService.getQueueStatus();

        // Assert
        assertEquals(1, status.waitingCount(), "대기 중인 메시지는 1개여야 합니다");
        assertFalse(status.isProcessing(), "초기에는 처리 중이 아니어야 합니다");
    }

    @Test
    @DisplayName("clearQueue로 큐의 모든 메시지를 삭제할 수 있다")
    void testClearQueue() {
        // Arrange
        for (int i = 1; i <= 3; i++) {
            BidQueueMessage message = BidQueueMessage.builder()
                    .auctionId((long) i)
                    .bidderId("user-" + i)
                    .bidPrice(10000L * i)
                    .enqueuedAt(LocalDateTime.now())
                    .build();
            bidQueueService.enqueueBid(message);
        }
        assertEquals(3, bidQueueService.getQueueSize());

        // Act
        bidQueueService.clearQueue();

        // Assert
        assertEquals(0, bidQueueService.getQueueSize(), "큐 초기화 후 크기는 0이어야 합니다");
    }

    @Test
    @DisplayName("JSON 직렬화/역직렬화가 올바르게 동작한다")
    void testJsonSerializationDeserialization() throws Exception {
        // Arrange
        LocalDateTime now = LocalDateTime.of(2026, 3, 30, 14, 30, 45);
        BidQueueMessage message = BidQueueMessage.builder()
                .auctionId(100L)
                .bidderId("test-user-123")
                .bidPrice(50000L)
                .enqueuedAt(now)
                .build();

        // Act
        bidQueueService.enqueueBid(message);
        BidQueueMessage result = bidQueueService.dequeueBid();

        // Assert
        assertNotNull(result);
        assertEquals(100L, result.getAuctionId());
        assertEquals("test-user-123", result.getBidderId());
        assertEquals(50000L, result.getBidPrice());
        assertEquals(now, result.getEnqueuedAt());
    }

    @Test
    @DisplayName("동시에 여러 메시지를 추가해도 정확하게 처리된다")
    void testConcurrentEnqueue() throws InterruptedException {
        // Arrange
        int threadCount = 10;
        int messagesPerThread = 5;

        // Act
        Thread[] threads = new Thread[threadCount];
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            threads[t] = new Thread(() -> {
                for (int i = 0; i < messagesPerThread; i++) {
                    BidQueueMessage message = BidQueueMessage.builder()
                            .auctionId((long) (threadId * 100 + i))
                            .bidderId("user-" + threadId + "-" + i)
                            .bidPrice(10000L * (i + 1))
                            .enqueuedAt(LocalDateTime.now())
                            .build();
                    bidQueueService.enqueueBid(message);
                }
            });
            threads[t].start();
        }

        // 모든 스레드 완료 대기
        for (Thread thread : threads) {
            thread.join();
        }

        // Assert - 모든 메시지가 정확히 추가되었는지 확인
        long expectedSize = (long) threadCount * messagesPerThread;
        assertEquals(expectedSize, bidQueueService.getQueueSize(),
                "동시 추가 후 큐 크기는 " + expectedSize + "여야 합니다");

        // 모든 메시지를 꺼낼 수 있는지 확인
        int count = 0;
        while (bidQueueService.getQueueSize() > 0) {
            BidQueueMessage message = bidQueueService.dequeueBid();
            assertNotNull(message);
            count++;
        }
        assertEquals(expectedSize, count, "모든 메시지를 꺼낼 수 있어야 합니다");
    }
}


