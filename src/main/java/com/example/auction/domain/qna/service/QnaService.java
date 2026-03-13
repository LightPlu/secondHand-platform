package com.example.auction.domain.qna.service;

import com.example.auction.domain.product.entity.Product;
import com.example.auction.domain.product.repository.ProductRepository;
import com.example.auction.domain.qna.dto.QnaAnswerRequest;
import com.example.auction.domain.qna.dto.QnaQuestionRequest;
import com.example.auction.domain.qna.dto.QnaResponse;
import com.example.auction.domain.qna.entity.Qna;
import com.example.auction.domain.qna.repository.QnaRepository;
import com.example.auction.domain.user.entity.User;
import com.example.auction.domain.user.repository.UserRepository;
import com.example.auction.global.exception.ProductNotFoundException;
import com.example.auction.global.exception.QnaNotFoundException;
import com.example.auction.global.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QnaService {

    private final QnaRepository qnaRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    @Transactional
    public QnaResponse createQuestion(String email, Long productId, QnaQuestionRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("상품을 찾을 수 없습니다: " + productId));

        Qna qna = Qna.builder()
                .product(product)
                .user(user)
                .question(request.getQuestion())
                .build();

        Qna savedQna = qnaRepository.save(qna);
        log.info("QnA 질문 등록 완료: qnaId={}, productId={}, userId={}",
                savedQna.getId(), productId, user.getId());
        return QnaResponse.from(savedQna);
    }

    public List<QnaResponse> getQnaByProduct(Long productId) {
        if (!productRepository.existsById(productId)) {
            throw new ProductNotFoundException("상품을 찾을 수 없습니다: " + productId);
        }

        return qnaRepository.findByProductIdOrderByCreatedAtDesc(productId)
                .stream()
                .map(QnaResponse::from)
                .collect(Collectors.toList());
    }

    public List<QnaResponse> getMyQna(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));

        return qnaRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(QnaResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public QnaResponse answerQna(String email, Long qnaId, QnaAnswerRequest request) {
        Qna qna = qnaRepository.findById(qnaId)
                .orElseThrow(() -> new QnaNotFoundException("QnA를 찾을 수 없습니다: " + qnaId));

        // 판매자만 답변 가능
        if (!qna.getProduct().getSeller().getEmail().equals(email)) {
            throw new UnauthorizedException("해당 상품 판매자만 답변할 수 있습니다.");
        }

        qna.answer(request.getAnswer());
        log.info("QnA 답변 등록 완료: qnaId={}, sellerEmail={}", qnaId, email);
        return QnaResponse.from(qna);
    }
}

