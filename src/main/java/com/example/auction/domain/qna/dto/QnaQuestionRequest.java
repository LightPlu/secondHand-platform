package com.example.auction.domain.qna.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "QnA 질문 등록 요청")
public class QnaQuestionRequest {

    @NotBlank(message = "질문 내용은 필수입니다.")
    @Schema(description = "질문 내용", example = "배터리 성능이 어느 정도인가요?")
    private String question;
}

