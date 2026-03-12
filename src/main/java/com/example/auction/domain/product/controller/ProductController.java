package com.example.auction.domain.product.controller;

import com.example.auction.domain.product.dto.ProductCreateRequest;
import com.example.auction.domain.product.dto.ProductResponse;
import com.example.auction.domain.product.dto.ProductUpdateRequest;
import com.example.auction.domain.product.enums.ProductStatus;
import com.example.auction.domain.product.service.ProductService;
import com.example.auction.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Product", description = "상품 등록 / 조회 / 수정 / 삭제 API")
public class ProductController {

    private final ProductService productService;

    /**
     * 상품 등록 (이미지 + 경매 포함)
     */
    @Operation(
            summary = "상품 등록",
            description = """
                    상품 정보와 이미지를 함께 등록합니다.
                    - 첫 번째 이미지가 썸네일로 지정됩니다.
                    - 경매 상품으로 등록 시 isAuction=true, startPrice, auctionStartTime, auctionEndTime 필수 입력.
                    """
    )
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductResponse> createProduct(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam String title,
            @RequestParam String description,
            @RequestParam String category,
            @RequestParam Long price,
            @RequestParam(defaultValue = "false") boolean isAuction,
            @RequestParam(required = false) Long startPrice,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime auctionStartTime,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime auctionEndTime,
            @RequestPart(value = "images", required = false) List<MultipartFile> images) {
        log.info("POST /api/products - 상품 등록 요청 (경매={})", isAuction);
        ProductCreateRequest request = ProductCreateRequest.builder()
                .title(title)
                .description(description)
                .category(category)
                .price(price)
                .isAuction(isAuction)
                .startPrice(startPrice)
                .auctionStartTime(auctionStartTime)
                .auctionEndTime(auctionEndTime)
                .build();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(productService.createProduct(userDetails.getUsername(), request, images));
    }

    /**
     * 상품 전체 목록 조회
     */
    @Operation(summary = "상품 목록 조회", description = "전체 상품 목록을 조회합니다.")
    @GetMapping
    public ResponseEntity<List<ProductResponse>> getProducts() {
        log.info("GET /api/products - 상품 목록 조회");
        return ResponseEntity.ok(productService.getProducts());
    }

    /**
     * 상태별 상품 목록 조회
     */
    @Operation(summary = "상태별 상품 목록 조회", description = "SALE / AUCTION / SOLD 상태별 상품 목록을 조회합니다.")
    @GetMapping("/status/{status}")
    public ResponseEntity<List<ProductResponse>> getProductsByStatus(@PathVariable ProductStatus status) {
        log.info("GET /api/products/status/{} - 상태별 상품 목록 조회", status);
        return ResponseEntity.ok(productService.getProductsByStatus(status));
    }

    /**
     * 카테고리별 상품 목록 조회
     */
    @Operation(summary = "카테고리별 상품 목록 조회", description = "카테고리와 상태로 상품 목록을 조회합니다.")
    @GetMapping("/category/{category}")
    public ResponseEntity<List<ProductResponse>> getProductsByCategory(
            @PathVariable String category,
            @RequestParam(defaultValue = "SALE") ProductStatus status) {
        log.info("GET /api/products/category/{} - 카테고리별 상품 조회", category);
        return ResponseEntity.ok(productService.getProductsByCategory(category, status));
    }

    /**
     * 키워드 검색
     */
    @Operation(summary = "상품 키워드 검색", description = "제목 기준으로 상품을 검색합니다.")
    @GetMapping("/search")
    public ResponseEntity<List<ProductResponse>> searchProducts(@RequestParam String keyword) {
        log.info("GET /api/products/search?keyword={} - 상품 검색", keyword);
        return ResponseEntity.ok(productService.searchProducts(keyword));
    }

    /**
     * 내 상품 목록 조회
     */
    @Operation(summary = "내 상품 목록 조회", description = "로그인한 사용자가 등록한 상품 목록을 조회합니다.")
    @GetMapping("/me")
    public ResponseEntity<List<ProductResponse>> getMyProducts(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.info("GET /api/products/me - 내 상품 목록 조회");
        return ResponseEntity.ok(productService.getMyProducts(userDetails.getUsername()));
    }

    /**
     * 상품 단건 조회
     */
    @Operation(summary = "상품 상세 조회", description = "상품 ID로 상품 상세 정보를 조회합니다.")
    @GetMapping("/{productId}")
    public ResponseEntity<ProductResponse> getProduct(@PathVariable Long productId) {
        log.info("GET /api/products/{} - 상품 상세 조회", productId);
        return ResponseEntity.ok(productService.getProduct(productId));
    }

    /**
     * 상품 수정
     */
    @Operation(summary = "상품 수정", description = "등록한 상품 정보를 수정합니다. 본인 상품만 수정 가능합니다.")
    @PatchMapping("/{productId}")
    public ResponseEntity<ProductResponse> updateProduct(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long productId,
            @Valid @RequestBody ProductUpdateRequest request) {
        log.info("PATCH /api/products/{} - 상품 수정 요청", productId);
        return ResponseEntity.ok(productService.updateProduct(userDetails.getUsername(), productId, request));
    }

    /**
     * 상품 상태 변경
     */
    @Operation(summary = "상품 상태 변경", description = "상품 상태를 SALE / AUCTION / SOLD 로 변경합니다.")
    @PatchMapping("/{productId}/status")
    public ResponseEntity<ProductResponse> changeProductStatus(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long productId,
            @RequestParam ProductStatus status) {
        log.info("PATCH /api/products/{}/status - 상품 상태 변경: {}", productId, status);
        return ResponseEntity.ok(productService.changeProductStatus(userDetails.getUsername(), productId, status));
    }

    /**
     * 상품 삭제
     */
    @Operation(summary = "상품 삭제", description = "상품을 삭제합니다. 경매 진행 중인 상품은 삭제할 수 없습니다.")
    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> deleteProduct(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long productId) {
        log.info("DELETE /api/products/{} - 상품 삭제 요청", productId);
        productService.deleteProduct(userDetails.getUsername(), productId);
        return ResponseEntity.noContent().build();
    }
}
