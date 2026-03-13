package com.example.auction.domain.qna.repository;

import com.example.auction.domain.qna.entity.Qna;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QnaRepository extends JpaRepository<Qna, Long> {

    List<Qna> findByProductIdOrderByCreatedAtDesc(Long productId);

    List<Qna> findByUserIdOrderByCreatedAtDesc(Long userId);
}

