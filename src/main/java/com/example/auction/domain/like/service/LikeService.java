package com.example.auction.domain.like.service;

import com.example.auction.domain.like.dto.LikeResponse;
import com.example.auction.domain.like.entity.LikeProduct;
import com.example.auction.domain.like.repository.LikeProductRepository;
import com.example.auction.domain.product.entity.Product;
import com.example.auction.domain.product.repository.ProductRepository;
import com.example.auction.domain.user.entity.User;
import com.example.auction.domain.user.repository.UserRepository;
import com.example.auction.global.exception.LikeAlreadyExistsException;
import com.example.auction.global.exception.LikeNotFoundException;
import com.example.auction.global.exception.ProductNotFoundException;
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
public class LikeService {

    private final LikeProductRepository likeProductRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    @Transactional
    public LikeResponse addLike(String email, Long productId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("상품을 찾을 수 없습니다: " + productId));

        if (likeProductRepository.existsByUserIdAndProductId(user.getId(), productId)) {
            throw new LikeAlreadyExistsException("이미 찜한 상품입니다.");
        }

        LikeProduct likeProduct = LikeProduct.builder()
                .user(user)
                .product(product)
                .build();

        LikeProduct savedLike = likeProductRepository.save(likeProduct);
        log.info("찜 등록 완료: userId={}, productId={}", user.getId(), productId);
        return LikeResponse.from(savedLike);
    }

    @Transactional
    public void removeLike(String email, Long productId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));

        LikeProduct likeProduct = likeProductRepository.findByUserIdAndProductId(user.getId(), productId)
                .orElseThrow(() -> new LikeNotFoundException("찜 내역이 없습니다."));

        likeProductRepository.delete(likeProduct);
        log.info("찜 취소 완료: userId={}, productId={}", user.getId(), productId);
    }

    public boolean isLiked(String email, Long productId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));
        return likeProductRepository.existsByUserIdAndProductId(user.getId(), productId);
    }

    public long getLikeCount(Long productId) {
        return likeProductRepository.countByProductId(productId);
    }

    public List<LikeResponse> getMyLikes(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));

        return likeProductRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(LikeResponse::from)
                .collect(Collectors.toList());
    }
}

