package com.example.auction.domain.auction.service;

import com.example.auction.domain.auction.dto.AuctionResponse;
import com.example.auction.domain.auction.entity.Auction;
import com.example.auction.domain.auction.enums.AuctionStatus;
import com.example.auction.domain.auction.repository.AuctionRepository;
import com.example.auction.domain.bid.entity.Bid;
import com.example.auction.domain.bid.repository.BidRepository;
import com.example.auction.domain.product.entity.Product;
import com.example.auction.domain.product.enums.ProductStatus;
import com.example.auction.domain.product.repository.ProductRepository;
import com.example.auction.domain.user.entity.User;
import com.example.auction.domain.user.enums.UserRole;
import com.example.auction.domain.user.enums.UserStatus;
import com.example.auction.domain.user.repository.UserRepository;
import com.example.auction.global.exception.InvalidAuctionStateException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class AuctionServiceTest {

    @Autowired
    private AuctionService auctionService;

    @Autowired
    private AuctionRepository auctionRepository;

    @Autowired
    private BidRepository bidRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("입찰이 없으면 경매 종료 시 유찰(FAILED) 처리된다")
    void syncAuctionStatus_whenNoBids_thenFailed() {
        System.out.println("테스트1시작");
        User seller = userRepository.save(createUser("seller-no-bid@test.com", "seller_no_bid"));
        Product product = productRepository.save(createProduct(seller, "유찰 테스트 상품"));

        Auction auction = auctionRepository.save(Auction.builder()
                .product(product)
                .startPrice(1000L)
                .currentPrice(1000L)
                .startTime(LocalDateTime.now().minusHours(2))
                .endTime(LocalDateTime.now().minusHours(1))
                .status(AuctionStatus.READY)
                .build());

        auctionService.syncAuctionStatus(auction.getId());
        auctionService.syncAuctionStatus(auction.getId());

        Auction updated = auctionRepository.findById(auction.getId()).orElseThrow();
        assertEquals(AuctionStatus.FAILED, updated.getStatus());
        assertNull(updated.getWinner());
        assertEquals(ProductStatus.SALE, updated.getProduct().getStatus());

        AuctionResponse result = auctionService.getAuctionResult(auction.getId());
        assertEquals(AuctionStatus.FAILED, result.getStatus());
        assertNull(result.getWinnerId());
        assertNull(result.getWinnerNickname());
        assertEquals(0L, result.getBidCount());
        System.out.println("테스트1종료");
    }

    @Test
    @DisplayName("입찰이 있으면 최고가 입찰자가 낙찰자로 지정되고 FINISHED 처리된다")
    void syncAuctionStatus_whenHasBids_thenFinishedWithWinner() {
        System.out.println("테스트2시작");
        User seller = userRepository.save(createUser("seller-bid@test.com", "seller_bid"));
        User bidder1 = userRepository.save(createUser("bidder1@test.com", "bidder_1"));
        User bidder2 = userRepository.save(createUser("bidder2@test.com", "bidder_2"));

        Product product = productRepository.save(createProduct(seller, "낙찰 테스트 상품"));

        Auction auction = auctionRepository.save(Auction.builder()
                .product(product)
                .startPrice(1000L)
                .currentPrice(1000L)
                .startTime(LocalDateTime.now().minusHours(2))
                .endTime(LocalDateTime.now().minusHours(1))
                .status(AuctionStatus.READY)
                .build());

        bidRepository.save(Bid.builder()
                .auction(auction)
                .user(bidder1)
                .bidPrice(1200L)
                .build());

        bidRepository.save(Bid.builder()
                .auction(auction)
                .user(bidder2)
                .bidPrice(1500L)
                .build());

        auctionService.syncAuctionStatus(auction.getId()); // READY -> RUNNING
        auctionService.syncAuctionStatus(auction.getId()); // RUNNING -> FINISHED

        Auction updated = auctionRepository.findById(auction.getId()).orElseThrow();
        assertEquals(AuctionStatus.FINISHED, updated.getStatus());
        assertNotNull(updated.getWinner());
        assertEquals(bidder2.getId(), updated.getWinner().getId());
        assertEquals(ProductStatus.SOLD, updated.getProduct().getStatus());

        AuctionResponse result = auctionService.getAuctionResult(auction.getId());
        assertEquals(AuctionStatus.FINISHED, result.getStatus());
        assertEquals(bidder2.getId(), result.getWinnerId());
        assertEquals("bidder_2", result.getWinnerNickname());
        assertEquals(2L, result.getBidCount());
        System.out.println("테스트2종료");
    }

    @Test
    @DisplayName("종료 전 경매는 결과 조회 시 예외가 발생한다")
    void getAuctionResult_whenNotFinished_thenThrowException() {
        System.out.println("테스트3시작");
        User seller = userRepository.save(createUser("seller-ready@test.com", "seller_ready"));
        Product product = productRepository.save(createProduct(seller, "진행 전 테스트 상품"));

        Auction auction = auctionRepository.save(Auction.builder()
                .product(product)
                .startPrice(1000L)
                .currentPrice(1000L)
                .startTime(LocalDateTime.now().minusHours(1))
                .endTime(LocalDateTime.now().plusHours(2))
                .status(AuctionStatus.READY)
                .build());

        auctionService.syncAuctionStatus(auction.getId());

        assertThrows(InvalidAuctionStateException.class,
                () -> auctionService.getAuctionResult(auction.getId()));
        System.out.println("테스트3종료");
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

    private Product createProduct(User seller, String title) {
        return Product.builder()
                .seller(seller)
                .title(title)
                .description("테스트 설명")
                .category("전자기기")
                .price(1000L)
                .status(ProductStatus.AUCTION)
                .build();
    }
}

