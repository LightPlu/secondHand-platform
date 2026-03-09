package com.example.auction.domain.product.repository;

import com.example.auction.domain.product.entity.Product;
import com.example.auction.domain.product.enums.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    // 전체 상품 목록
    List<Product> findAllByOrderByCreatedAtDesc();

    // 상태별 상품 목록
    List<Product> findByStatusOrderByCreatedAtDesc(ProductStatus status);

    // 카테고리별 상품 목록
    List<Product> findByCategoryAndStatusOrderByCreatedAtDesc(String category, ProductStatus status);

    // 판매자별 상품 목록
    List<Product> findBySellerIdOrderByCreatedAtDesc(Long sellerId);

    // 키워드 검색 (제목)
    List<Product> findByTitleContainingAndStatusOrderByCreatedAtDesc(String keyword, ProductStatus status);
}
