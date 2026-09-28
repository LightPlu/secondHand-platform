package com.example.auction.domain.bid.service;

import com.example.auction.domain.bid.dto.BidQueueMessage;
import com.example.auction.domain.bid.dto.BidRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class BidQueueWorker {

    // 경매 처리 락 TTL. 드레인 도중 워커가 죽어도 이 시간 후 다른 워커가 이어받는다.
    private static final Duration LOCK_TTL = Duration.ofSeconds(10);
    // 한 번의 락 점유에서 처리할 최대 건수. TTL 안에 끝내고 락을 놓아 다른 경매에도 기회를 준다.
    private static final int MAX_DRAIN_PER_TICK = 200;

    private final BidQueueService bidQueueService;
    private final BidService bidService;

    /**
     * 대기 중인 경매를 순회하며, 경매별 락을 잡은 경우에만 해당 경매 큐를 순차 처리한다.
     * 경매마다 소비자를 1명으로 고정하므로, 인스턴스가 여러 개여도 경매 단위 순차성이 보장된다.
     */
    @Scheduled(fixedDelay = 50, initialDelay = 1000)
    public void processBidQueue() {
        for (Long auctionId : bidQueueService.getActiveAuctionIds()) {
            drainAuction(auctionId);
        }
    }

    private void drainAuction(Long auctionId) {
        String token = UUID.randomUUID().toString();
        // 다른 인스턴스/워커가 이미 이 경매를 처리 중이면 건너뛴다.
        if (!bidQueueService.tryAcquireProcessingLock(auctionId, token, LOCK_TTL)) {
            return;
        }

        try {
            int processed = 0;
            BidQueueMessage message;
            while (processed < MAX_DRAIN_PER_TICK
                    && (message = bidQueueService.dequeueBid(auctionId)) != null) {
                processOne(message);
                processed++;
            }
            // 큐가 비었으면 인덱스에서 정리(락 보유 상태에서 안전하게 수행)
            bidQueueService.removeFromIndexIfEmpty(auctionId);
        } finally {
            bidQueueService.releaseProcessingLock(auctionId, token);
        }
    }

    private void processOne(BidQueueMessage message) {
        try {
            log.info("[BID WORKER] 입찰 처리 시작: auctionId={}, bidderId={}, bidPrice={}",
                    message.getAuctionId(), message.getBidderId(), message.getBidPrice());

            // 실제 입찰 처리 (경매별 락으로 직렬화되므로 서비스단 락 없이 순차 처리)
            BidRequest bidRequest = new BidRequest(message.getBidPrice());
            bidService.placeBidFromQueue(message.getBidderId(), message.getAuctionId(), bidRequest);

            log.info("[BID WORKER] 입찰 처리 완료: auctionId={}, bidderId={}, bidPrice={}, 남은큐크기={}",
                    message.getAuctionId(), message.getBidderId(), message.getBidPrice(),
                    bidQueueService.getQueueSize(message.getAuctionId()));
        } catch (Exception e) {
            log.error("[BID WORKER] 입찰 처리 실패: auctionId={}, bidderId={}, reason={}",
                    message.getAuctionId(), message.getBidderId(), e.getMessage());
        }
    }

    /**
     * 큐 모니터링 (로깅용)
     */
    @Scheduled(fixedDelay = 5000)
    public void monitorQueue() {
        BidQueueService.QueueStatus status = bidQueueService.getQueueStatus();
        if (status.waitingCount() > 0) {
            log.info("[BID QUEUE MONITOR] 대기 입찰: {}, 처리 중: {}", status.waitingCount(), status.isProcessing());
        }
    }
}
