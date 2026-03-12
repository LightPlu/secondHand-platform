package com.example.auction.domain.product.service;

import com.example.auction.domain.product.dto.ProductCreateRequest;
import com.example.auction.domain.product.dto.ProductResponse;
import com.example.auction.domain.product.dto.ProductUpdateRequest;
import com.example.auction.domain.product.entity.Product;
import com.example.auction.domain.product.entity.ProductImage;
import com.example.auction.domain.product.enums.ProductStatus;
import com.example.auction.domain.product.repository.ProductImageRepository;
import com.example.auction.domain.product.repository.ProductRepository;
import com.example.auction.domain.auction.entity.Auction;
import com.example.auction.domain.auction.enums.AuctionStatus;
import com.example.auction.domain.auction.repository.AuctionRepository;
import com.example.auction.domain.user.entity.User;
import com.example.auction.domain.user.repository.UserRepository;
import com.example.auction.global.exception.ProductNotFoundException;
import com.example.auction.global.exception.UnauthorizedException;
import com.example.auction.global.util.R2UploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final AuctionRepository auctionRepository;
    private final UserRepository userRepository;
    private final R2UploadService r2UploadService;

    // 상품 등록 (이미지 + 경매 포함)
    @Transactional
    public ProductResponse createProduct(String email, ProductCreateRequest request, List<MultipartFile> images) {
        User seller = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));

        // 경매 상품 유효성 검사
        if (Boolean.TRUE.equals(request.getIsAuction())) {
            if (request.getStartPrice() == null) {
                throw new RuntimeException("경매 시작가는 필수입니다.");
            }
            if (request.getAuctionStartTime() == null || request.getAuctionEndTime() == null) {
                throw new RuntimeException("경매 시작/종료 시간은 필수입니다.");
            }
            if (request.getAuctionEndTime().isBefore(request.getAuctionStartTime())) {
                throw new RuntimeException("경매 종료 시간은 시작 시간보다 이후여야 합니다.");
            }
        }

        // 경매 여부에 따라 상품 상태 결정
        ProductStatus productStatus = Boolean.TRUE.equals(request.getIsAuction())
                ? ProductStatus.AUCTION : ProductStatus.SALE;

        Product product = Product.builder()
                .seller(seller)
                .title(request.getTitle())
                .description(request.getDescription())
                .category(request.getCategory())
                .price(request.getPrice())
                .status(productStatus)
                .build();

        Product savedProduct = productRepository.save(product);

        // 이미지 업로드 및 저장
        if (images != null && !images.isEmpty()) {
            for (int i = 0; i < images.size(); i++) {
                MultipartFile image = images.get(i);
                if (!image.isEmpty()) {
                    String imageUrl = r2UploadService.upload(image, "products");
                    ProductImage productImage = ProductImage.builder()
                            .product(savedProduct)
                            .imageUrl(imageUrl)
                            .isThumbnail(i == 0)
                            .build();
                    productImageRepository.save(productImage);
                    savedProduct.getImages().add(productImage);
                }
            }
        }

        // 경매 등록
        if (Boolean.TRUE.equals(request.getIsAuction())) {
            Auction auction = Auction.builder()
                    .product(savedProduct)
                    .startPrice(request.getStartPrice())
                    .currentPrice(request.getStartPrice())
                    .startTime(request.getAuctionStartTime())
                    .endTime(request.getAuctionEndTime())
                    .status(AuctionStatus.READY)
                    .build();
            auctionRepository.save(auction);
            log.info("경매 등록 완료: productId={}, startTime={}, endTime={}",
                    savedProduct.getId(), request.getAuctionStartTime(), request.getAuctionEndTime());
        }

        log.info("상품 등록 완료: productId={}, sellerId={}, 이미지 수={}, 경매={}",
                savedProduct.getId(), seller.getId(),
                images != null ? images.size() : 0, Boolean.TRUE.equals(request.getIsAuction()));

        return ProductResponse.from(savedProduct);
    }

    // 상품 전체 목록 조회
    public List<ProductResponse> getProducts() {
        return productRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(ProductResponse::from)
                .collect(Collectors.toList());
    }

    // 상태별 상품 목록 조회
    public List<ProductResponse> getProductsByStatus(ProductStatus status) {
        return productRepository.findByStatusOrderByCreatedAtDesc(status)
                .stream()
                .map(ProductResponse::from)
                .collect(Collectors.toList());
    }

    // 카테고리별 상품 목록 조회
    public List<ProductResponse> getProductsByCategory(String category, ProductStatus status) {
        return productRepository.findByCategoryAndStatusOrderByCreatedAtDesc(category, status)
                .stream()
                .map(ProductResponse::from)
                .collect(Collectors.toList());
    }

    // 키워드 검색
    public List<ProductResponse> searchProducts(String keyword) {
        return productRepository.findByTitleContainingAndStatusOrderByCreatedAtDesc(keyword, ProductStatus.SALE)
                .stream()
                .map(ProductResponse::from)
                .collect(Collectors.toList());
    }

    // 내 상품 목록 조회
    public List<ProductResponse> getMyProducts(String email) {
        User seller = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));
        return productRepository.findBySellerIdOrderByCreatedAtDesc(seller.getId())
                .stream()
                .map(ProductResponse::from)
                .collect(Collectors.toList());
    }

    // 상품 단건 조회
    public ProductResponse getProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("상품을 찾을 수 없습니다: " + productId));
        return ProductResponse.from(product);
    }

    // 상품 수정
    @Transactional
    public ProductResponse updateProduct(String email, Long productId, ProductUpdateRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("상품을 찾을 수 없습니다: " + productId));

        if (!product.getSeller().getEmail().equals(email)) {
            throw new UnauthorizedException("상품 수정 권한이 없습니다.");
        }

        if (product.getStatus() == ProductStatus.SOLD) {
            throw new RuntimeException("판매 완료된 상품은 수정할 수 없습니다.");
        }

        product.updateProduct(request.getTitle(), request.getDescription(),
                request.getCategory(), request.getPrice());
        log.info("상품 수정 완료: productId={}", productId);

        return ProductResponse.from(product);
    }

    // 상품 삭제
    @Transactional
    public void deleteProduct(String email, Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("상품을 찾을 수 없습니다: " + productId));

        if (!product.getSeller().getEmail().equals(email)) {
            throw new UnauthorizedException("상품 삭제 권한이 없습니다.");
        }

        if (product.getStatus() == ProductStatus.AUCTION) {
            throw new RuntimeException("경매 진행 중인 상품은 삭제할 수 없습니다.");
        }

        // R2 이미지 삭제
        product.getImages().forEach(image -> r2UploadService.delete(image.getImageUrl()));

        productRepository.delete(product);
        log.info("상품 삭제 완료: productId={}", productId);
    }

    // 상품 상태 변경
    @Transactional
    public ProductResponse changeProductStatus(String email, Long productId, ProductStatus status) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("상품을 찾을 수 없습니다: " + productId));

        if (!product.getSeller().getEmail().equals(email)) {
            throw new UnauthorizedException("상품 상태 변경 권한이 없습니다.");
        }

        product.changeStatus(status);
        log.info("상품 상태 변경 완료: productId={}, status={}", productId, status);

        return ProductResponse.from(product);
    }
}
