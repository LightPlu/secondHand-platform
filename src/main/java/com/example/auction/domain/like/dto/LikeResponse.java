package com.example.auction.domain.like.dto;

import com.example.auction.domain.like.entity.LikeProduct;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "찜 응답")
public class LikeResponse {

    @Schema(description = "찜 ID", example = "1")
    private Long likeId;

    @Schema(description = "상품 ID", example = "10")
    private Long productId;

    @Schema(description = "상품 제목", example = "맥북 프로 14인치")
    private String productTitle;

    @Schema(description = "찜한 사용자 ID", example = "2")
    private Long userId;

    @Schema(description = "찜 생성 시간")
    private LocalDateTime createdAt;

    public static LikeResponse from(LikeProduct likeProduct) {
        return LikeResponse.builder()
                .likeId(likeProduct.getId())
                .productId(likeProduct.getProduct().getId())
                .productTitle(likeProduct.getProduct().getTitle())
                .userId(likeProduct.getUser().getId())
                .createdAt(likeProduct.getCreatedAt())
                .build();
    }
}
