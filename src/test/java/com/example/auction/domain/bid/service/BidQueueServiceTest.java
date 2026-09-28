package com.example.auction.domain.bid.service;

import com.example.auction.domain.bid.dto.BidQueueMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BidQueueServiceTest {

    // 상품(경매)별 큐 키와 인덱스 키
    private static final String AUCTION_QUEUE_KEY = "bid:queue:1";
    private static final String INDEX_KEY = "bid:queue:index";

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ListOperations<String, Object> listOperations;

    @Mock
    private SetOperations<String, Object> setOperations;

    private ObjectMapper objectMapper;
    private BidQueueService bidQueueService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        bidQueueService = new BidQueueService(redisTemplate, objectMapper);
        when(redisTemplate.opsForList()).thenReturn(listOperations);
    }

    @Test
    @DisplayName("enqueueBid 호출 시 경매별 큐에 JSON을 저장하고 인덱스에 경매 id를 등록한다")
    void enqueueBid_shouldPushToAuctionQueueAndRegisterIndex() throws Exception {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);

        BidQueueMessage message = BidQueueMessage.builder()
                .auctionId(1L)
                .bidderId("user-1")
                .bidPrice(15000L)
                .enqueuedAt(LocalDateTime.of(2026, 3, 30, 12, 0))
                .build();

        when(listOperations.size(AUCTION_QUEUE_KEY)).thenReturn(1L);

        bidQueueService.enqueueBid(message);

        ArgumentCaptor<Object> valueCaptor = ArgumentCaptor.forClass(Object.class);
        verify(listOperations).rightPush(eq(AUCTION_QUEUE_KEY), valueCaptor.capture());
        // 경매 id가 처리 대상 인덱스에 등록되어야 한다
        verify(setOperations).add(INDEX_KEY, "1");

        String storedJson = (String) valueCaptor.getValue();
        BidQueueMessage restored = objectMapper.readValue(storedJson, BidQueueMessage.class);

        assertEquals(message.getAuctionId(), restored.getAuctionId());
        assertEquals(message.getBidderId(), restored.getBidderId());
        assertEquals(message.getBidPrice(), restored.getBidPrice());
        assertEquals(message.getEnqueuedAt(), restored.getEnqueuedAt());
    }

    @Test
    @DisplayName("dequeueBid(auctionId) 호출 시 해당 경매 큐에서 꺼낸 JSON을 객체로 복원한다")
    void dequeueBid_shouldPopFromAuctionQueueAndDeserialize() throws Exception {
        BidQueueMessage message = BidQueueMessage.builder()
                .auctionId(2L)
                .bidderId("user-2")
                .bidPrice(21000L)
                .enqueuedAt(LocalDateTime.of(2026, 3, 30, 12, 30))
                .build();

        String json = objectMapper.writeValueAsString(message);
        when(listOperations.leftPop("bid:queue:2")).thenReturn(json);

        BidQueueMessage result = bidQueueService.dequeueBid(2L);

        assertNotNull(result);
        assertEquals(message.getAuctionId(), result.getAuctionId());
        assertEquals(message.getBidderId(), result.getBidderId());
        assertEquals(message.getBidPrice(), result.getBidPrice());
        assertEquals(message.getEnqueuedAt(), result.getEnqueuedAt());
        verify(listOperations).leftPop("bid:queue:2");
    }
}
