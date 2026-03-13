package com.example.auction.domain.qna.dto;

import com.example.auction.domain.qna.entity.Qna;
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
@Schema(description = "QnA 응답")
public class QnaResponse {

    @Schema(description = "QnA ID", example = "1")
    private Long qnaId;

    @Schema(description = "상품 ID", example = "10")
    private Long productId;

    @Schema(description = "질문자 ID", example = "2")
    private Long userId;

    @Schema(description = "질문자 닉네임", example = "구매희망자")
    private String userNickname;

    @Schema(description = "질문 내용", example = "사용감이 어느 정도인가요?")
    private String question;

    @Schema(description = "답변 내용", example = "생활기스 약간 있고 기능은 정상입니다.")
    private String answer;

    @Schema(description = "작성일시")
    private LocalDateTime createdAt;

    public static QnaResponse from(Qna qna) {
        return QnaResponse.builder()
                .qnaId(qna.getId())
                .productId(qna.getProduct().getId())
                .userId(qna.getUser().getId())
                .userNickname(qna.getUser().getNickname())
                .question(qna.getQuestion())
                .answer(qna.getAnswer())
                .createdAt(qna.getCreatedAt())
                .build();
    }
}

