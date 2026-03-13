package com.example.auction.domain.like.repository;

import com.example.auction.domain.like.entity.LikeProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LikeProductRepository extends JpaRepository<LikeProduct, Long> {

    boolean existsByUserIdAndProductId(Long userId, Long productId);

    Optional<LikeProduct> findByUserIdAndProductId(Long userId, Long productId);

    List<LikeProduct> findByUserIdOrderByCreatedAtDesc(Long userId);

    long countByProductId(Long productId);
}

