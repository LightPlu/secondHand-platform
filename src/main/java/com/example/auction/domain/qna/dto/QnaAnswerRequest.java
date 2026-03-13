package com.example.auction.domain.qna.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "QnA 답변 등록 요청")
public class QnaAnswerRequest {

    @NotBlank(message = "답변 내용은 필수입니다.")
    @Schema(description = "답변 내용", example = "배터리 성능은 90%입니다.")
    private String answer;
}

