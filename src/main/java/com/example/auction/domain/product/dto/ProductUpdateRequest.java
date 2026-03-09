package com.example.auction.domain.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "상품 수정 요청")
public class ProductUpdateRequest {

    @Size(max = 100, message = "제목은 100자 이하여야 합니다.")
    @Schema(description = "변경할 제목 (null이면 기존 값 유지)", example = "아이폰 15 급처합니다")
    private String title;

    @Schema(description = "변경할 설명 (null이면 기존 값 유지)", example = "급하게 팝니다. 네고 가능합니다.")
    private String description;

    @Schema(description = "변경할 카테고리 (null이면 기존 값 유지)", example = "전자기기")
    private String category;

    @Min(value = 0, message = "가격은 0원 이상이어야 합니다.")
    @Schema(description = "변경할 가격 (null이면 기존 값 유지)", example = "450000")
    private Long price;
}

