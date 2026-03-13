package com.example.auction.domain.qna.controller;

import com.example.auction.domain.qna.dto.QnaAnswerRequest;
import com.example.auction.domain.qna.dto.QnaQuestionRequest;
import com.example.auction.domain.qna.dto.QnaResponse;
import com.example.auction.domain.qna.service.QnaService;
import com.example.auction.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "QnA", description = "상품 QnA API")
public class QnaController {

    private final QnaService qnaService;

    @Operation(summary = "질문 등록", description = "상품에 질문을 등록합니다.")
    @PostMapping("/api/products/{productId}/qna")
    public ResponseEntity<QnaResponse> createQuestion(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long productId,
            @Valid @RequestBody QnaQuestionRequest request
    ) {
        log.info("POST /api/products/{}/qna - 질문 등록", productId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(qnaService.createQuestion(userDetails.getUsername(), productId, request));
    }

    @Operation(summary = "상품 QnA 목록 조회", description = "특정 상품의 QnA 목록을 최신순으로 조회합니다.")
    @GetMapping("/api/products/{productId}/qna")
    public ResponseEntity<List<QnaResponse>> getQnaByProduct(@PathVariable Long productId) {
        log.info("GET /api/products/{}/qna - 상품 QnA 목록 조회", productId);
        return ResponseEntity.ok(qnaService.getQnaByProduct(productId));
    }

    @Operation(summary = "내 질문 목록 조회", description = "로그인한 사용자의 질문 목록을 최신순으로 조회합니다.")
    @GetMapping("/api/qna/me")
    public ResponseEntity<List<QnaResponse>> getMyQna(@AuthenticationPrincipal CustomUserDetails userDetails) {
        log.info("GET /api/qna/me - 내 질문 목록 조회");
        return ResponseEntity.ok(qnaService.getMyQna(userDetails.getUsername()));
    }

    @Operation(summary = "QnA 답변 등록", description = "해당 상품의 판매자가 질문에 답변을 등록합니다.")
    @PatchMapping("/api/qna/{qnaId}/answer")
    public ResponseEntity<QnaResponse> answerQna(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long qnaId,
            @Valid @RequestBody QnaAnswerRequest request
    ) {
        log.info("PATCH /api/qna/{}/answer - QnA 답변 등록", qnaId);
        return ResponseEntity.ok(qnaService.answerQna(userDetails.getUsername(), qnaId, request));
    }
}

