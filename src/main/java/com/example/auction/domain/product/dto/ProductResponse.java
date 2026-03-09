package com.example.auction.domain.product.dto;

import com.example.auction.domain.product.entity.Product;
import com.example.auction.domain.product.entity.ProductImage;
import com.example.auction.domain.product.enums.ProductStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "상품 응답")
public class ProductResponse {

    @Schema(description = "상품 ID", example = "1")
    private Long id;

    @Schema(description = "판매자 ID", example = "1")
    private Long sellerId;

    @Schema(description = "판매자 닉네임", example = "판매왕")
    private String sellerNickname;

    @Schema(description = "상품 제목", example = "아이폰 15 팝니다")
    private String title;

    @Schema(description = "상품 설명", example = "구매한지 6개월 된 아이폰 15입니다.")
    private String description;

    @Schema(description = "카테고리", example = "전자기기")
    private String category;

    @Schema(description = "가격", example = "500000")
    private Long price;

    @Schema(description = "상품 상태", example = "SALE")
    private ProductStatus status;

    @Schema(description = "이미지 목록")
    private List<ProductImageResponse> images;

    @Schema(description = "등록일시")
    private LocalDateTime createdAt;

    public static ProductResponse from(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .sellerId(product.getSeller().getId())
                .sellerNickname(product.getSeller().getNickname())
                .title(product.getTitle())
                .description(product.getDescription())
                .category(product.getCategory())
                .price(product.getPrice())
                .status(product.getStatus())
                .images(product.getImages().stream()
                        .map(ProductImageResponse::from)
                        .collect(Collectors.toList()))
                .createdAt(product.getCreatedAt())
                .build();
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "상품 이미지 응답")
    public static class ProductImageResponse {

        @Schema(description = "이미지 ID", example = "1")
        private Long id;

        @Schema(description = "이미지 URL", example = "https://your-r2-url/products/uuid.jpg")
        private String imageUrl;

        @Schema(description = "썸네일 여부", example = "true")
        private Boolean isThumbnail;

        public static ProductImageResponse from(ProductImage image) {
            return ProductImageResponse.builder()
                    .id(image.getId())
                    .imageUrl(image.getImageUrl())
                    .isThumbnail(image.getIsThumbnail())
                    .build();
        }
    }
}

