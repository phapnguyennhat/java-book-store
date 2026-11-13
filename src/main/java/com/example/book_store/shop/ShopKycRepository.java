package com.example.book_store.shop;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface ShopKycRepository extends JpaRepository<ShopKyc, Long> {
    @Query("""
            select new com.example.book_store.shop.KycSummary(k.shopId, s.name, k.sellerType, k.legalName, k.status, k.submittedAt)
            from ShopKyc k join Shop s on s.id = k.shopId where k.status = :status""")
    Page<KycSummary> findSummaries(KycStatus status, Pageable pageable);
}
