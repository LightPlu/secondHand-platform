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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class BidServiceConcurrencyTest {

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
    @DisplayName("동시에 입찰이 진행된다면 발생하는 결과")
    void placeBid_samePrice_concurrently() throws InterruptedException {
        // given
        User seller = userRepository.save(createUser("seller@concurrency.com", "seller"));
        User bidder1 = userRepository.save(createUser("bidder1@concurrency.com", "bidder1"));
        User bidder2 = userRepository.save(createUser("bidder2@concurrency.com", "bidder2"));

        Product product = productRepository.save(createProduct(seller));
        Auction auction = auctionRepository.save(createAuction(product, 10_000L));

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);  // 동시에 출발 신호
        CountDownLatch doneLatch = new CountDownLatch(threadCount); // 완료 대기

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // bidder1 입찰
        executor.submit(() -> {
            try {
                startLatch.await(); // 출발 신호 대기
                bidService.placeBid(bidder1.getEmail(), auction.getId(), new BidRequest(11_000L));
                successCount.incrementAndGet();
            } catch (Exception e) {
                failCount.incrementAndGet();
                System.out.println("[bidder1 실패] " + e.getMessage());
            } finally {
                doneLatch.countDown();
            }
        });

        // bidder2 입찰 (동일 금액)
        executor.submit(() -> {
            try {
                startLatch.await(); // 출발 신호 대기
                bidService.placeBid(bidder2.getEmail(), auction.getId(), new BidRequest(11_000L));
                successCount.incrementAndGet();
            } catch (Exception e) {
                failCount.incrementAndGet();
                System.out.println("[bidder2 실패] " + e.getMessage());
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown(); // 동시 출발
        doneLatch.await();      // 두 스레드 완료 대기
        executor.shutdown();

        // then
        long bidCount = bidRepository.countByAuctionId(auction.getId());
        Auction updated = auctionRepository.findById(auction.getId()).orElseThrow();

        System.out.println("=== 동시성 테스트 결과 ===");
        System.out.println("성공 횟수: " + successCount.get());
        System.out.println("실패 횟수: " + failCount.get());
        System.out.println("DB 저장된 입찰 수: " + bidCount);
        System.out.println("최종 현재가: " + updated.getCurrentPrice());

        // 기대: 동시성 문제가 있으면 2건 모두 성공 → 중복 입찰 발생
        // 정상: 1건만 성공해야 함
        assertEquals(1, successCount.get(),
                "동시성 제어가 없으면 같은 금액으로 2건 모두 성공할 수 있습니다. 현재 성공 수: " + successCount.get());
    }

    @Test
    @DisplayName("동시에 다른 금액으로 입찰을 50회 반복했을 때 최종 저장 결과를 확인한다")
    void placeBid_diffPrice_concurrently() throws InterruptedException {
        int rounds = 50;
        List<Long> finalPrices = new ArrayList<>();
        int finalHighPriceCount = 0;
        int finalLowPriceCount = 0;

        for (int round = 1; round <= rounds; round++) {
            User seller = userRepository.save(createUser("seller" + round + "@concurrency.com", "seller" + round));
            User bidder1 = userRepository.save(createUser("bidder1-" + round + "@concurrency.com", "bidder1_" + round));
            User bidder2 = userRepository.save(createUser("bidder2-" + round + "@concurrency.com", "bidder2_" + round));

            Product product = productRepository.save(createProduct(seller));
            Auction auction = auctionRepository.save(createAuction(product, 10_000L));

            ExecutorService executor = Executors.newFixedThreadPool(2);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(2);

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            executor.submit(() -> {
                try {
                    startLatch.await();
                    bidService.placeBid(bidder1.getEmail(), auction.getId(), new BidRequest(11_500L));
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });

            executor.submit(() -> {
                try {
                    startLatch.await();
                    bidService.placeBid(bidder2.getEmail(), auction.getId(), new BidRequest(11_000L));
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });

            startLatch.countDown();
            doneLatch.await();
            executor.shutdown();

            long bidCount = bidRepository.countByAuctionId(auction.getId());
            Auction updated = auctionRepository.findById(auction.getId()).orElseThrow();
            long finalPrice = updated.getCurrentPrice();

            finalPrices.add(finalPrice);
            if (finalPrice == 11_500L) {
                finalHighPriceCount++;
            } else if (finalPrice == 11_000L) {
                finalLowPriceCount++;
            }

            System.out.printf("[ROUND %02d] success=%d fail=%d bids=%d finalPrice=%d%n",
                    round, successCount.get(), failCount.get(), bidCount, finalPrice);

            assertEquals(2, successCount.get() + failCount.get(), "각 회차는 요청 2건이 모두 처리되어야 합니다.");
        }

        System.out.println("================ SUMMARY ================");
        System.out.println("총 반복 횟수: " + rounds);
        System.out.println("최종가 11,500 회수: " + finalHighPriceCount);
        System.out.println("최종가 11,000 회수: " + finalLowPriceCount);
        System.out.println("최종가 목록: " + finalPrices);
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
}
