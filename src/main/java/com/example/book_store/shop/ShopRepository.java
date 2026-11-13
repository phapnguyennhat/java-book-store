package com.example.book_store.shop;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface ShopRepository extends JpaRepository<Shop, Long> {
    Optional<Shop> findByOwnerId(Long ownerId);

    Optional<Shop> findBySlug(String slug);

    boolean existsByOwnerId(Long ownerId);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Long id);
}
