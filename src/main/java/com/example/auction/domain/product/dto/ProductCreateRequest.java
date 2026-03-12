package com.example.auction.domain.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "상품 등록 요청")
public class ProductCreateRequest {

    @NotBlank(message = "제목은 필수입니다.")
    @Size(max = 100, message = "제목은 100자 이하여야 합니다.")
    @Schema(description = "상품 제목", example = "아이폰 15 팝니다")
    private String title;

    @NotBlank(message = "상품 설명은 필수입니다.")
    @Schema(description = "상품 설명", example = "구매한지 6개월 된 아이폰 15입니다. 상태 좋습니다.")
    private String description;

    @NotBlank(message = "카테고리는 필수입니다.")
    @Schema(description = "카테고리", example = "전자기기")
    private String category;

    @NotNull(message = "가격은 필수입니다.")
    @Min(value = 0, message = "가격은 0원 이상이어야 합니다.")
    @Schema(description = "가격 (원)", example = "500000")
    private Long price;

    // 경매 여부 (true이면 경매 상품으로 등록)
    @Schema(description = "경매 여부 (true이면 경매 상품으로 등록)", example = "false")
    private Boolean isAuction;

    // 경매 시작가 (isAuction = true 일 때 필수)
    @Schema(description = "경매 시작가 (경매 상품일 때 필수)", example = "100000")
    private Long startPrice;

    // 경매 시작 시간 (isAuction = true 일 때 필수)
    @Schema(description = "경매 시작 시간 (경매 상품일 때 필수)", example = "2026-03-10T10:00:00")
    private LocalDateTime auctionStartTime;

    // 경매 종료 시간 (isAuction = true 일 때 필수)
    @Schema(description = "경매 종료 시간 (경매 상품일 때 필수)", example = "2026-03-17T10:00:00")
    private LocalDateTime auctionEndTime;
}
