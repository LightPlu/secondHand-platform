package com.example.auction.domain.bid.service;

import com.example.auction.domain.bid.dto.BidQueueMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class BidQueueService {

    // 상품(경매)별로 큐를 분리한다: bid:queue:{auctionId}
    // → 서로 다른 경매는 병렬로 처리하면서, 한 경매 안에서는 순차성을 유지할 수 있는 단위가 된다.
    private static final String BID_QUEUE_KEY_PREFIX = "bid:queue:";
    // 대기 중인 메시지가 있는 경매 id 집합. 워커는 이 인덱스만 보고 처리 대상을 찾는다(SCAN 회피).
    private static final String BID_QUEUE_INDEX_KEY = "bid:queue:index";
    // 경매별 처리 소유권 락: 여러 인스턴스가 같은 경매를 동시에 소비하지 못하게 한다(single consumer per auction).
    private static final String BID_WORKER_LOCK_PREFIX = "bid:worker:lock:";

    // 락 소유자만 해제할 수 있도록 하는 compare-and-delete 스크립트
    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class
    );

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    private String queueKey(Long auctionId) {
        return BID_QUEUE_KEY_PREFIX + auctionId;
    }

    private String lockKey(Long auctionId) {
        return BID_WORKER_LOCK_PREFIX + auctionId;
    }

    /**
     * 입찰 요청을 해당 경매의 큐에 추가하고, 처리 대상 인덱스에 경매 id를 등록한다.
     */
    public void enqueueBid(BidQueueMessage message) {
        Long auctionId = message.getAuctionId();
        try {
            String json = objectMapper.writeValueAsString(message);
            redisTemplate.opsForList().rightPush(queueKey(auctionId), json);
            // 큐에 넣은 뒤 인덱스에 등록(멱등). 순서상 항상 rightPush 다음에 SADD 한다.
            redisTemplate.opsForSet().add(BID_QUEUE_INDEX_KEY, String.valueOf(auctionId));
            log.info("[BID QUEUE] 입찰 요청 추가: auctionId={}, bidderId={}, bidPrice={}, queueSize={}",
                    auctionId, message.getBidderId(), message.getBidPrice(), getQueueSize(auctionId));
        } catch (Exception e) {
            log.error("[BID QUEUE] 입찰 요청 추가 실패: {}", e.getMessage(), e);
            throw new RuntimeException("큐에 입찰 요청을 추가할 수 없습니다.", e);
        }
    }

    /**
     * 특정 경매 큐에서 입찰 요청을 하나 꺼낸다 (FIFO).
     */
    public BidQueueMessage dequeueBid(Long auctionId) {
        try {
            Object value = redisTemplate.opsForList().leftPop(queueKey(auctionId));
            if (value == null) {
                return null;
            }
            BidQueueMessage message = objectMapper.readValue((String) value, BidQueueMessage.class);
            log.debug("[BID QUEUE] 입찰 요청 처리: auctionId={}, bidderId={}, bidPrice={}",
                    message.getAuctionId(), message.getBidderId(), message.getBidPrice());
            return message;
        } catch (Exception e) {
            log.error("[BID QUEUE] 입찰 요청 꺼내기 실패: auctionId={}, reason={}", auctionId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 경매를 지정하지 않고 대기 중인 아무 경매에서 하나 꺼낸다(하위 호환/단일 큐 소비용).
     * 비어 버린 경매는 인덱스에서 정리한다.
     */
    public BidQueueMessage dequeueBid() {
        for (Long auctionId : getActiveAuctionIds()) {
            BidQueueMessage message = dequeueBid(auctionId);
            if (message != null) {
                return message;
            }
            removeFromIndexIfEmpty(auctionId);
        }
        return null;
    }

    /**
     * 대기 중인 메시지가 있는 경매 id 목록.
     */
    public Set<Long> getActiveAuctionIds() {
        Set<Object> members = redisTemplate.opsForSet().members(BID_QUEUE_INDEX_KEY);
        if (members == null || members.isEmpty()) {
            return Collections.emptySet();
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (Object member : members) {
            ids.add(Long.valueOf(String.valueOf(member)));
        }
        return ids;
    }

    /**
     * 특정 경매 큐의 대기 건수.
     */
    public long getQueueSize(Long auctionId) {
        Long size = redisTemplate.opsForList().size(queueKey(auctionId));
        return size != null ? size : 0L;
    }

    /**
     * 전체 대기 건수(모든 경매 합계).
     */
    public long getQueueSize() {
        long total = 0L;
        for (Long auctionId : getActiveAuctionIds()) {
            total += getQueueSize(auctionId);
        }
        return total;
    }

    /**
     * 경매 처리 소유권 락 획득 시도. 성공하면 이 워커만 해당 경매를 드레인할 수 있다.
     */
    public boolean tryAcquireProcessingLock(Long auctionId, String token, Duration ttl) {
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey(auctionId), token, ttl);
        return Boolean.TRUE.equals(acquired);
    }

    /**
     * 처리 소유권 락 해제. 자신이 획득한 락일 때만 해제한다(compare-and-delete).
     */
    public void releaseProcessingLock(Long auctionId, String token) {
        try {
            redisTemplate.execute(
                    RELEASE_LOCK_SCRIPT,
                    Collections.singletonList(lockKey(auctionId)),
                    token
            );
        } catch (Exception e) {
            log.warn("[BID QUEUE] 처리 락 해제 실패(무시): auctionId={}, reason={}", auctionId, e.getMessage());
        }
    }

    /**
     * 큐가 비었으면 인덱스에서 제거한다. 제거 직후 생산자가 넣은 메시지가 있으면 다시 등록해 유실을 막는다.
     * (경매 처리 락을 보유한 상태에서 호출하는 것을 전제로 한다.)
     */
    public void removeFromIndexIfEmpty(Long auctionId) {
        if (getQueueSize(auctionId) > 0) {
            return;
        }
        redisTemplate.opsForSet().remove(BID_QUEUE_INDEX_KEY, String.valueOf(auctionId));
        // SREM 직전/직후에 enqueue된 메시지가 있으면 인덱스를 복구한다.
        if (getQueueSize(auctionId) > 0) {
            redisTemplate.opsForSet().add(BID_QUEUE_INDEX_KEY, String.valueOf(auctionId));
        }
    }

    /**
     * 큐 상태 조회 (대기 중 개수, 처리 중 여부).
     */
    public QueueStatus getQueueStatus() {
        long waitingSize = getQueueSize();
        boolean isProcessing = false;
        for (Long auctionId : getActiveAuctionIds()) {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(lockKey(auctionId)))) {
                isProcessing = true;
                break;
            }
        }
        return new QueueStatus(waitingSize, isProcessing);
    }

    /**
     * 모든 경매 큐와 인덱스를 초기화 (테스트용).
     */
    public void clearQueue() {
        Set<Long> auctionIds = getActiveAuctionIds();
        for (Long auctionId : auctionIds) {
            redisTemplate.delete(queueKey(auctionId));
            redisTemplate.delete(lockKey(auctionId));
        }
        redisTemplate.delete(BID_QUEUE_INDEX_KEY);
        log.info("[BID QUEUE] 큐 초기화 완료 (경매 {}개)", auctionIds.size());
    }

    public record QueueStatus(long waitingCount, boolean isProcessing) {
    }
}
